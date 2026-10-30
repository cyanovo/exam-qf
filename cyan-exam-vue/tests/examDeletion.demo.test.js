import test from 'node:test'
import assert from 'node:assert/strict'
import {execFileSync} from 'node:child_process'
import {createRequire} from 'node:module'

const base='http://127.0.0.1:8182', ui='http://127.0.0.1:5178'
function sql(statement) {
  assert.ok(process.env.MYSQL_PWD)
  return execFileSync('D:/app/mysql-8.0.45-winx64/bin/mysql.exe',
    ['-u','root','--default-character-set=utf8mb4','-N','-B','cyan_exam_python_demo_20261029','-e',statement],{encoding:'utf8'}).trim()
}
async function api(token,path,method='GET',body,ok=true) {
  const r=await(await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body)})).json()
  if(ok) assert.equal(r.code,200,JSON.stringify(r)); else assert.notEqual(r.code,200,JSON.stringify(r))
  return r
}
test('exam deletion: expired draft, batch atomicity, protected data and browser actions',
  {skip:process.env.EXAM_RELEASE_V2_TEST!=='1',timeout:120000},async t=>{
  const owner=(await api(null,'/login','POST',{username:'demo_owner',password:'Demo2026!Onsite'})).token
  const stamp=Date.now(), prefix=`Delete test ${stamp}`
  function fixture(suffix,expired=false,ownerId=91001) {
    return Number(sql(`INSERT INTO exam_info(exam_name,start_time,end_time,owner_user_id,create_by) VALUES('${prefix} ${suffix}',DATE_ADD(NOW(),INTERVAL ${expired?-2:1} HOUR),DATE_ADD(NOW(),INTERVAL ${expired?-1:2} HOUR),${ownerId},'delete-fix-test');SELECT LAST_INSERT_ID();`))
  }
  const first=fixture('row'), second=fixture('batch A',true), third=fixture('batch B')
  const taskId=(await api(owner,`/exam/onsite/${first}/tasks`,'POST',{title:'Draft task',description:'Deletion fixture',durationMinutes:10,judgeId:91002,items:[{title:'Item',maxScore:10}]})).data
  await api(owner,`/exam/exam/${first}/students`,'PUT',[91004])
  await api(owner,`/exam/results/${first}/reviewers`,'POST',{userId:91003})
  sql(`UPDATE exam_info SET start_time=DATE_SUB(NOW(),INTERVAL 2 HOUR),end_time=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE exam_id=${first} AND create_by='delete-fix-test'`)
  const published=fixture('published')
  sql(`UPDATE exam_info SET published_at=NOW() WHERE exam_id=${published} AND create_by='delete-fix-test'`)
  await api(owner,`/exam/exam/${second},${published}`,'DELETE',undefined,false)
  assert.equal(sql(`SELECT COUNT(*) FROM exam_info WHERE exam_id=${second}`),'1')
  const material=fixture('material')
  sql(`INSERT INTO exam_incident(exam_id,user_id,created_by,category,title,description) VALUES(${material},91004,91001,'DEVICE','Test evidence','Preserve record')`)
  await api(owner,`/exam/exam/${material}`,'DELETE',undefined,false)
  assert.equal(sql(`SELECT COUNT(*) FROM exam_incident WHERE exam_id=${material}`),'1')
  const foreign=fixture('other owner',true,91003)
  await api(owner,`/exam/exam/${foreign}`,'DELETE',undefined,false)
  assert.equal(sql(`SELECT COUNT(*) FROM exam_info WHERE exam_id=${foreign}`),'1')
  const require=createRequire(import.meta.url)
  const {chromium}=require(process.env.PLAYWRIGHT_MODULE_PATH||'playwright')
  const browser=await chromium.launch({headless:true,channel:'msedge'})
  t.after(()=>browser.close())
  const context=await browser.newContext({viewport:{width:1440,height:960}})
  await context.addCookies([{name:'Exam-Token-127-0-0-1-8182',value:owner,url:ui}])
  const page=await context.newPage()
  await page.goto(ui+'/exam-management')
  async function search(name) {
    await page.getByPlaceholder('请输入考试名称').fill(name)
    const response=page.waitForResponse(r=>r.url().includes('/exam/exam/list?'))
    await page.getByRole('button',{name:'搜索',exact:true}).click()
    await response
  }
  await search(prefix+' row')
  const row=page.getByRole('row').filter({hasText:prefix+' row'})
  await row.getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog').getByRole('button',{name:'确定',exact:true}).click()
  await row.waitFor({state:'hidden'})
  assert.equal(sql(`SELECT COUNT(*) FROM exam_info WHERE exam_id=${first}`),'0')
  for(const table of ['exam_student','exam_question','exam_reviewer','exam_onsite_task'])
    assert.equal(sql(`SELECT COUNT(*) FROM ${table} WHERE exam_id=${first}`),'0',table)
  assert.equal(sql(`SELECT COUNT(*) FROM exam_onsite_event WHERE task_id=${taskId}`),'0')
  assert.equal(sql(`SELECT COUNT(*) FROM exam_management_event WHERE exam_id=${first} AND action='DELETE'`),'1')
  await search(prefix+' batch')
  for(const suffix of ['A','B']) await page.getByRole('row').filter({hasText:prefix+' batch '+suffix}).locator('label.el-checkbox').click()
  await page.locator('.el-row.mb8').getByRole('button',{name:'删除',exact:true}).click()
  await page.getByRole('dialog').getByRole('button',{name:'确定',exact:true}).click()
  await page.getByRole('row').filter({hasText:prefix+' batch A'}).waitFor({state:'hidden'})
  assert.equal(sql(`SELECT COUNT(*) FROM exam_info WHERE exam_id IN(${second},${third})`),'0')
  await page.screenshot({path:'tests/deletion-desktop.png',fullPage:true})
  console.log(JSON.stringify({deleted:[first,second,third],protected:[published,material,foreign]}))
})
