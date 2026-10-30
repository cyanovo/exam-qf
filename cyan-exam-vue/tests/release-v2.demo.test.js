import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'

const enabled=process.env.EXAM_RELEASE_V2_TEST==='1'
const base='http://127.0.0.1:8182', ui='http://127.0.0.1:5178'
const db='cyan_exam_python_demo_20261029'
function sql(statement) {
  assert.ok(process.env.MYSQL_PWD,'Explicit demo DB credentials required')
  return execFileSync('D:/app/mysql-8.0.45-winx64/bin/mysql.exe',
    ['-u','root','--default-character-set=utf8mb4','-N','-B',db,'-e',statement],{encoding:'utf8'}).trim()
}
async function api(token,path,method='GET',body,ok=true) {
  const response=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body)})
  const result=await response.json()
  if(ok) assert.equal(result.code,200,`${method} ${path}: ${JSON.stringify(result)}`)
  else assert.notEqual(result.code,200,`${path} should reject`)
  return result
}
async function login(username,password='Demo2026!Onsite') {return (await api(null,'/login','POST',{username,password})).token}
function exam(name,users=[91004]) {
  const id=Number(sql(`INSERT INTO exam_info(exam_name,start_time,end_time,status,owner_user_id,create_by,create_time) VALUES('${name}',DATE_ADD(NOW(),INTERVAL 1 HOUR),DATE_ADD(NOW(),INTERVAL 3 HOUR),'0',91001,'release-v2-test',NOW());SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO exam_student(exam_id,user_id) VALUES ${users.map(u=>`(${id},${u})`).join(',')}`)
  return id
}
async function task(owner,id) {
  return (await api(owner,`/exam/onsite/${id}/tasks`,'POST',{title:'现场程序演示',description:'展示运行结果，说明实现思路。',durationMinutes:10,judgeId:91002,items:[{title:'演示完成度',maxScore:20}]})).data
}
async function configure(owner,id,taskId,releasePolicy='TOTAL') {
  await api(owner,`/exam/manage/${id}`,'PUT',{durationMinutes:15,earlyEntryMinutes:90,policy:{instructions:'请按章节完成任务，成果文件须在交卷前提交。',tieRule:'TASK_SCORE',tieTaskId:taskId,releasePolicy,retentionDays:30,entries:[{kind:'ONSITE',id:taskId,section:'第一章 · 程序演示',description:'独立完成演示与答辩。',hint:'提交源代码与运行证据。'}]}})
}
async function upload(token,id,taskId,userId,purpose,name,text,incidentId,ok=true) {
  const data=new FormData()
  for(const [k,v] of Object.entries({userId,taskId,purpose,incidentId})) if(v!=null) data.append(k,String(v))
  data.append('file',new Blob([text]),name)
  const result=await (await fetch(`${base}/exam/dossier/${id}/files`,{method:'POST',headers:{Authorization:`Bearer ${token}`},body:data})).json()
  if(ok) assert.equal(result.code,200,JSON.stringify(result));else assert.notEqual(result.code,200)
  return result.data
}

test('release v2: real API lifecycle, authorization, files, results and responsive UI',{skip:!enabled,timeout:240000},async t=>{
  const stamp=Date.now()
  const owner=await login('demo_owner'), judge=await login('demo_judge'), reviewer=await login('demo_reviewer'), student=await login('demo_student')
  const username=`v2_${stamp}`
  const newcomer=Number(sql(`INSERT INTO sys_user(dept_id,user_name,nick_name,password,status,del_flag,create_by,create_time) SELECT dept_id,'${username}','V2 Test Candidate',password,'0','0','release-v2-test',NOW() FROM sys_user WHERE user_id=91004;SELECT LAST_INSERT_ID();`))
  sql(`INSERT INTO sys_user_role(user_id,role_id) VALUES(${newcomer},100)`)
  const newToken=await login(username)
  assert.equal((await api(newToken,'/getInfo')).isDefaultModifyPwd,true)
  assert.equal((await api(newToken,'/exam/student/waitInfo','GET',undefined,false)).code,428)

  const require=createRequire(import.meta.url)
  const {chromium}=require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
  const browser=await chromium.launch({headless:true,channel:'msedge',args:['--disable-gpu','--use-fake-ui-for-media-stream','--use-fake-device-for-media-stream']})
  t.after(()=>browser.close())
  async function pageFor(token) {
    const ctx=await browser.newContext({viewport:{width:1440,height:960}})
    await ctx.addCookies([{name:'Exam-Token-127-0-0-1-8182',value:token,url:ui}])
    return ctx.newPage()
  }
  const pw=await pageFor(newToken)
  await pw.goto(`${ui}/exam/wait`)
  await pw.waitForURL('**/change-password')
  assert.equal(await pw.getByRole('button',{name:'取消',exact:true}).count(),0)
  await api(newToken,'/system/user/profile/updatePwd','PUT',{oldPassword:'Demo2026!Onsite',newPassword:'weak'},false)
  await api(newToken,'/system/user/profile/updatePwd','PUT',{oldPassword:'Demo2026!Onsite',newPassword:'V2Changed2026!'})
  await api(newToken,'/exam/student/waitInfo')

  const id=exam(`Release V2 verified ${stamp}`), tid=await task(owner,id)
  await configure(owner,id,tid)
  await api(owner,`/exam/results/${id}/reviewers`,'POST',{userId:91003})
  await api(owner,`/exam/exam/${id}/publish`,'POST')
  assert.equal((await api(owner,`/exam/manage/${id}`)).data.state,'READY')
  assert.ok((await api(owner,`/exam/manage/${id}`)).data.history.some(e=>e.action==='PUBLISH'))
  assert.equal((await api(owner,`/exam/onsite/${id}`)).data.tasks[0].state,'OPEN')
  await api(student,`/exam/student/${id}/session`,'POST',undefined,false)
  await api(owner,`/exam/manage/${id}/withdraw`,'POST',{reason:'Verify pre-entry withdrawal'})
  assert.equal((await api(owner,`/exam/onsite/${id}`)).data.tasks[0].state,'DRAFT')
  await api(owner,`/exam/exam/${id}/publish`,'POST')
  // Advance only the newly created synthetic fixture, never an existing demo exam.
  sql(`UPDATE exam_info SET start_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE exam_id=${id} AND create_by='release-v2-test'`)
  await api(student,`/exam/student/${id}/session`,'POST')
  const questions=(await api(student,`/exam/student/questions?examId=${id}`)).data
  assert.equal(questions.questions.length,0);assert.equal(questions.onsiteTasks.length,1)
  assert.ok(new Date(questions.endTime)-new Date(questions.serverTime)<=15*60000)
  const fileId=await upload(student,id,tid,91004,'OUTPUT','answer.py',"print('demo')")
  const bytes=await fetch(`${base}/exam/dossier/files/${fileId}`,{headers:{Authorization:`Bearer ${student}`}})
  assert.equal(bytes.headers.get('x-content-type-options'),'nosniff')
  assert.equal(await bytes.text(),"print('demo')")
  await api(newToken,`/exam/dossier/files/${fileId}`,'GET',undefined,false)
  await api(newToken,`/exam/dossier/${id}`,'GET',undefined,false)
  await upload(student,id,tid,91004,'OUTPUT','script.html','<script>alert(1)</script>',null,false)
  await upload(student,id,tid,91004,'OUTPUT','image.png','not a PNG',null,false)
  const incidentId=(await api(student,`/exam/dossier/${id}/incidents`,'POST',{userId:91004,taskId:tid,category:'DEVICE',title:'设备异常测试',description:'记录发生时间与处理过程。'})).data
  await upload(student,id,tid,91004,'EVIDENCE','evidence.txt','Synthetic evidence',incidentId)
  await api(student,`/exam/dossier/${id}/incidents/${incidentId}/process`,'POST',{revision:1,opinion:'Not permitted'},false)
  await api(owner,`/exam/dossier/${id}/incidents/${incidentId}/process`,'POST',{revision:1,opinion:'已受理'})
  await api(owner,`/exam/dossier/${id}/incidents/${incidentId}/resolve`,'POST',{revision:1,opinion:'Stale'},false)
  await api(owner,`/exam/dossier/${id}/incidents/${incidentId}/resolve`,'POST',{revision:2,opinion:'设备已恢复，证据已核对'})
  await api(owner,`/exam/dossier/${id}/incidents/${incidentId}/approve`,'POST',{revision:3,opinion:'Self approval'},false)
  await api(reviewer,`/exam/dossier/${id}/incidents/${incidentId}/approve`,'POST',{revision:3,opinion:'独立复核通过'})
  assert.equal((await api(student,`/exam/dossier/${id}`)).data.incidents[0].events.length,4)
  await api(student,`/exam/student/${id}/finish`,'POST')
  await upload(student,id,tid,91004,'OUTPUT','late.txt','late',null,false)
  await api(judge,`/exam/onsite/${id}/tasks/${tid}/scores/91004`,'PUT',{points:[18],note:'Verified',revision:0,submit:true})
  await api(owner,`/exam/onsite/${id}/tasks/${tid}/lock`,'POST',{reason:'Scores complete'})
  await api(owner,`/exam/manage/${id}/terminate`,'POST',{reason:'Synthetic test finished'})
  await api(owner,`/exam/results/${id}/request`,'POST',{reason:'Review pure onsite result'})
  const result=(await api(owner,`/exam/results/${id}`)).data
  assert.equal(result.snapshot.rows[0].total,18)
  assert.equal(result.snapshot.taskStatistics[0].rate,90)
  await api(reviewer,`/exam/results/${id}/review/approve`,'POST',{reason:'Checked'})
  await api(reviewer,`/exam/results/${id}/review/release`,'POST',{reason:'Release'})
  const mine=(await api(student,`/exam/results/mine/${id}`)).data
  assert.equal(mine.total,18);assert.ok(!('rank' in mine));assert.ok(!('scores' in mine))
  const csv=await (await fetch(`${base}/exam/results/${id}/export`,{method:'POST',headers:{Authorization:`Bearer ${owner}`}})).text()
  assert.ok(csv.includes('"C1"'));assert.ok(!csv.includes('Demo Student'));assert.ok(!csv.includes('"91004"'))
  assert.equal((await api(owner,`/exam/results/${id}/history`)).data.length,1)
  await api(owner,`/exam/manage/${id}/archive`,'POST',{reason:'Archive verified test'})
  await api(owner,`/exam/results/${id}/correction`,'POST',{reason:'Archived'},false)
  await upload(owner,id,tid,91004,'RESOURCE','blocked.txt','archived',null,false)

  const expired=exam(`V2 deadline ${stamp}`), expiredTask=await task(owner,expired)
  await configure(owner,expired,expiredTask)
  await api(owner,`/exam/exam/${expired}/publish`,'POST')
  sql(`UPDATE exam_info SET start_time=DATE_SUB(NOW(),INTERVAL 20 MINUTE) WHERE exam_id=${expired} AND create_by='release-v2-test'`)
  await api(student,`/exam/student/${expired}/session`,'POST')
  sql(`UPDATE exam_session SET started_at=DATE_SUB(NOW(),INTERVAL 16 MINUTE) WHERE exam_id=${expired} AND user_id=91004`)
  await upload(student,expired,expiredTask,91004,'OUTPUT','expired.py','print(1)',null,false)
  await api(owner,`/exam/manage/${expired}/terminate`,'POST',{reason:'Deadline fixture complete'})
  assert.equal(sql(`SELECT COUNT(*) FROM exam_session WHERE exam_id=${expired} AND status='IN_PROGRESS'`),'0')

  // A fresh draft and a fresh live exam are left for interactive demonstration.
  const draft=exam(`V2 编排演示 ${stamp}`, [newcomer]), draftTask=await task(owner,draft)
  await configure(owner,draft,draftTask,'DETAIL')
  const candidates=(await api(owner,`/exam/manage/${draft}/students`)).data
  assert.ok(candidates.some(u=>u.userId===newcomer),'Student accounts without extended profiles must be selectable')
  assert.ok(candidates.every(u=>!('password' in u)),'Candidates must not expose account secrets')
  await api(student,`/exam/manage/${draft}/students`,'GET',undefined,false)
  await api(owner,`/exam/exam/${draft}/students`,'PUT',[newcomer,91001],false)
  await api(owner,`/exam/exam/${id}/students`,'PUT',[91004],false)
  const rosterPage=await pageFor(owner)
  await rosterPage.goto(`${ui}/exam/manage/${draft}`)
  await rosterPage.getByRole('button',{name:'分配学生',exact:true}).click()
  const rosterDialog=rosterPage.getByRole('dialog',{name:'分配学生'})
  await rosterDialog.getByRole('textbox',{name:'查找学生'}).fill('demo_student')
  const studentCheckbox=rosterDialog.getByRole('checkbox',{name:'选择学生 demo_student',exact:true})
  await rosterDialog.locator('label[aria-label="选择学生 demo_student"]').click()
  assert.equal(await studentCheckbox.isChecked(),true)
  await rosterPage.screenshot({path:'tests/v2-roster-desktop.png',fullPage:true})
  await rosterPage.setViewportSize({width:390,height:844})
  await rosterPage.screenshot({path:'tests/v2-roster-mobile.png',fullPage:true})
  assert.ok(await rosterPage.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'roster mobile overflow')
  await rosterPage.setViewportSize({width:1440,height:960})
  await rosterDialog.getByRole('button',{name:'保存名单'}).click()
  await rosterDialog.waitFor({state:'hidden'})
  assert.deepEqual(new Set((await api(owner,`/exam/exam/${draft}/studentIds`)).data),new Set([newcomer,91004]))
  await rosterPage.getByRole('button',{name:'分配学生',exact:true}).click()
  await rosterDialog.getByRole('textbox',{name:'查找学生'}).fill(username)
  assert.equal(await rosterDialog.getByRole('checkbox',{name:`选择学生 ${username}`,exact:true}).isChecked(),true)
  await rosterDialog.getByRole('button',{name:'取消',exact:true}).click()
  const routes=(await api(owner,'/getRouters')).data
  function infoPath(items,parent='') {
    for(const item of items) {
      const path=item.path.startsWith('/')?item.path:`${parent.replace(/\/$/,'')}/${item.path}`
      if(item.component==='system/info/index') return path
      const child=infoPath(item.children || [],path)
      if(child) return child
    }
  }
  assert.ok(infoPath(routes),'Exam management menu must be available')
  await rosterPage.goto(ui+infoPath(routes))
  await rosterPage.getByPlaceholder('请输入考试名称').fill(`V2 编排演示 ${stamp}`)
  await rosterPage.getByRole('button',{name:'搜索',exact:true}).click()
  const examRow=rosterPage.getByRole('row').filter({hasText:`V2 编排演示 ${stamp}`})
  await examRow.getByRole('button',{name:'分配学生',exact:true}).click()
  await rosterDialog.getByRole('textbox',{name:'查找学生'}).fill('demo_student')
  assert.equal(await rosterDialog.getByRole('checkbox',{name:'选择学生 demo_student',exact:true}).isChecked(),true)
  await rosterDialog.getByRole('button',{name:'取消',exact:true}).click()
  await examRow.getByRole('button',{name:'选择题目',exact:true}).click()
  await rosterPage.getByRole('dialog',{name:'选择题目',exact:true}).waitFor()
  await rosterPage.getByRole('dialog',{name:'选择题目',exact:true}).getByRole('button',{name:'取 消'}).click()
  await examRow.getByRole('button',{name:'修改',exact:true}).click()
  const editDialog=rosterPage.getByRole('dialog',{name:'修改考试',exact:true})
  await editDialog.waitFor()
  await editDialog.getByPlaceholder('请输入备注').fill('Verified UI edit')
  await editDialog.getByRole('button',{name:'确 定'}).click()
  await editDialog.waitFor({state:'hidden'})
  assert.equal((await api(owner,`/exam/exam/${draft}`)).data.remark,'Verified UI edit')
  const toolbar=rosterPage.locator('.el-row.mb8')
  assert.equal(await toolbar.getByRole('button',{name:'分配学生',exact:true}).isDisabled(),true)
  await examRow.locator('label.el-checkbox').click()
  for(const name of ['修改','选择题目','分配学生','发布']) {
    assert.equal(await toolbar.getByRole('button',{name,exact:true}).isEnabled(),true,`${name} should be enabled for a selected draft`)
  }
  await toolbar.getByRole('button',{name:'分配学生',exact:true}).click()
  await rosterDialog.getByRole('textbox',{name:'查找学生'}).fill('demo_student')
  assert.equal(await rosterDialog.getByRole('checkbox',{name:'选择学生 demo_student',exact:true}).isChecked(),true)
  await rosterDialog.getByRole('button',{name:'取消',exact:true}).click()
  await rosterDialog.waitFor({state:'hidden'})
  await rosterPage.screenshot({path:'tests/v2-exam-info-desktop.png',fullPage:true})
  await rosterPage.close()
  const live=exam(`V2 现场作答演示 ${stamp}`,[newcomer]), liveTask=await task(owner,live)
  await configure(owner,live,liveTask,'TOTAL_RANK')
  await api(owner,`/exam/exam/${live}/publish`,'POST')
  sql(`UPDATE exam_info SET start_time=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE exam_id=${live} AND create_by='release-v2-test'`)
  const pages=[{token:owner,path:`/exam/manage/${draft}`,selector:'main.app-container',name:'manage'},
    {token:owner,path:`/exam/dossier/${id}`,selector:'.dossier',name:'dossier'},
    {token:newToken,path:`/exam/answer?examId=${live}`,selector:'.exam-answer',name:'answer'}]
  for(const spec of pages) {
    const page=await pageFor(spec.token),errors=[]
    page.on('pageerror',e=>errors.push(e.message))
    await page.goto(ui+spec.path)
    await page.locator(spec.selector).waitFor()
    await page.waitForTimeout(1200)
    if(spec.name==='manage') {
      await page.getByRole('tab',{name:'章节与任务顺序'}).click()
      await page.getByRole('tabpanel',{name:'章节与任务顺序'}).getByRole('cell').filter({hasText:'现场程序演示'}).waitFor()
    }
    if(spec.name==='answer') await page.getByText('第一章 · 程序演示',{exact:true}).waitFor()
    await page.screenshot({path:`tests/v2-${spec.name}-desktop.png`,fullPage:true})
    await page.setViewportSize({width:390,height:844});await page.waitForTimeout(500)
    await page.screenshot({path:`tests/v2-${spec.name}-mobile.png`,fullPage:true})
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),`${spec.name} mobile overflow`)
    assert.deepEqual(errors,[],`${spec.name} page errors`)
    await page.close()
  }
  console.log(JSON.stringify({verifiedExam:id,draftExam:draft,liveExam:live,testUsername:username,checks:'API + desktop/mobile screenshots passed'}))
})
