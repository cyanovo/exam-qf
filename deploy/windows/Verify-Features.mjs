import assert from 'node:assert/strict'
import { readFileSync, mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'

const cfg=JSON.parse(readFileSync(new URL('../../.runtime-release/test-config.json',import.meta.url),'utf8'))
assert.ok(cfg.Database.startsWith('cyan_restore_'), 'Tests only run against an isolated restored database')
const base=`http://127.0.0.1:${cfg.ApiPort}`, ui=`http://127.0.0.1:${cfg.WebPort}`
function sql(text) {
  return execFileSync(cfg.Mysql,['--protocol=TCP',`--host=${cfg.DatabaseHost}`,`--port=${cfg.DatabasePort}`,`--user=${cfg.DatabaseUser}`,'--default-character-set=utf8mb4','-N','-B',cfg.Database,'-e',text],
    {encoding:'utf8',env:{...process.env,MYSQL_PWD:cfg.DatabasePassword}}).trim()
}
async function api(token,path,method='GET',body,ok=true) {
  const result=await (await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body)})).json()
  if(ok) assert.equal(result.code,200,`${method} ${path}: ${JSON.stringify(result)}`)
  else assert.notEqual(result.code,200,`Must reject ${method} ${path}`)
  return result
}
const stamp=Date.now(), names={}
for(const [kind,roles] of Object.entries({admin:['system_admin','teacher','reviewer'],teacher:['teacher'],student:['student']})) {
  const name=`fe_${kind[0]}_${stamp}`; names[kind]=name
  const id=Number(sql(`INSERT INTO sys_user(dept_id,user_name,nick_name,password,status,del_flag,pwd_update_date,create_time)
    SELECT dept_id,'${name}','Feature Verification',password,'0','0',NOW(),NOW() FROM sys_user WHERE user_name='demo_owner';
    SELECT LAST_INSERT_ID();`))
  assert.ok(id)
  sql(`INSERT INTO sys_user_role(user_id,role_id) SELECT ${id},role_id FROM sys_role WHERE del_flag='0' AND role_key IN (${roles.map(x=>`'${x}'`).join(',')})`)
}
const tokens={}
for(const [kind,username] of Object.entries(names)) tokens[kind]=(await api(null,'/login','POST',{username,password:'Demo2026!Onsite'})).token
const a=tokens.admin
await api(tokens.student,'/system/equipment','GET',undefined,false)
await api(tokens.student,'/question/question/list','GET',undefined,false)
await api(tokens.teacher,'/system/equipment','GET',undefined,false)
const q={category:'PYTHON',difficulty:1,questionType:'FILL_BLANK',title:`Review test ${stamp}`,answer:'print',subTitle:'',remark:''}
await api(tokens.teacher,'/question/question','POST',q)
let row=(await api(a,`/question/question/list?title=${encodeURIComponent(q.title)}`)).rows.find(x=>x.title===q.title)
assert.ok(row)
const id=row.questionId
async function fresh() {return (await api(a,`/question/question/${id}`)).data}
async function move(action,reason='',ok=true,token=a,revision=row.revision) {
  await api(token,`/question/question/${id}/${['APPROVE','REJECT'].includes(action)?'review':'workflow'}`,'POST',{action,reason,revision},ok)
  row=await fresh()
}
assert.equal(row.reviewState,'DRAFT')
await move('APPROVE','',false)
await move('SUBMIT')
assert.equal(row.reviewState,'REVIEW')
await api(a,'/question/question','PUT',{...row,title:'Should not edit pending review'},false)
await move('APPROVE','',false,tokens.teacher)
await move('REJECT','',false)
await move('REJECT','Clarify expected answer')
assert.equal(row.reviewState,'DRAFT')
await move('SUBMIT')
await move('APPROVE')
assert.equal(row.reviewState,'PUBLISHED')
await move('DISABLE')
assert.equal(row.reviewState,'DISABLED')
await move('REOPEN')
await move('SUBMIT')
await move('APPROVE')
const oldRevision=row.revision
await api(a,'/question/question','PUT',{...row,remark:'Edited after publication'})
row=await fresh()
assert.equal(row.reviewState,'DRAFT')
await api(a,'/question/question','PUT',{...row,revision:oldRevision,remark:'Stale edit'},false)
await move('SUBMIT','',false,a,oldRevision)
const history=(await api(a,`/question/question/${id}/history`)).data
assert.ok(history.length>=10)
assert.equal(new Set(history.map(e=>e.revision)).size,history.length)
console.log('PASS question workflow, immutable history, stale writes, permission checks')

const station={code:`WS-${stamp}`,location:'Room 101',contact:'Duty teacher',remark:''}
await api(a,'/system/equipment/stations','POST',{code:`OPTIONAL-${stamp}`,location:'Room 102'})
const optional=(await api(a,'/system/equipment')).data.stations.find(x=>x.code===`OPTIONAL-${stamp}`)
assert.equal(optional.contact,'')
await api(a,`/system/equipment/stations/${optional.id}?revision=${optional.revision}`,'DELETE')
await api(a,'/system/equipment/stations','POST',station)
await api(a,'/system/equipment/stations','POST',station,false)
let inventory=(await api(a,'/system/equipment')).data
const ws=inventory.stations.find(x=>x.code===station.code)
const device={code:`ROBOT-${stamp}`,model:'Teaching robot',assetState:'AVAILABLE',stationId:ws.id,contact:'Duty teacher',remark:''}
await api(a,'/system/equipment/devices','POST',{...device,stationId:999999999},false)
await api(a,'/system/equipment/devices','POST',{...device,assetState:'RETIRED'},false)
await api(a,'/system/equipment/devices','POST',device)
await api(a,`/system/equipment/stations/${ws.id}?revision=${ws.revision}`,'DELETE',undefined,false)
inventory=(await api(a,'/system/equipment')).data
const robot=inventory.devices.find(x=>x.code===device.code)
await api(a,'/system/equipment/devices','POST',{...robot,assetState:'MAINTENANCE'})
await api(a,'/system/equipment/devices','POST',{...robot,remark:'stale'},false)
await api(a,`/system/equipment/devices/${robot.id}?revision=${robot.revision}`,'DELETE',undefined,false)
console.log('PASS equipment CRUD, duplicate, binding, retired state, occupied station, stale revision')

if(process.env.PLAYWRIGHT_MODULE_PATH) {
  const require=createRequire(import.meta.url), {chromium}=require(process.env.PLAYWRIGHT_MODULE_PATH)
  const browser=await chromium.launch({channel:'msedge',headless:true})
  try {
    const context=await browser.newContext({viewport:{width:1440,height:960}})
    await context.addCookies([{name:'Admin-Token',value:a,url:ui}])
    const page=await context.newPage(),errors=[]
    page.on('pageerror',error=>errors.push(error.message))
    mkdirSync(new URL('../../.runtime-release/screenshots/',import.meta.url),{recursive:true})
    await page.goto(ui+'/system/equipment')
    await page.getByRole('heading',{name:'设备与工位'}).waitFor()
    await page.getByRole('cell',{name:device.code,exact:true}).waitFor()
    const downloadPromise=page.waitForEvent('download')
    await page.getByRole('button',{name:'导出',exact:true}).click()
    const download=await downloadPromise
    assert.equal(download.suggestedFilename(),'设备台账.csv')
    await download.saveAs(new URL('../../.runtime-release/equipment-export.csv',import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1'))
    await page.screenshot({path:'.runtime-release/screenshots/equipment-desktop.png',fullPage:true})
    // Navigate using the actual server's menu route, not a test-only route.
    const routers=(await api(a,'/getRouters')).data
    function find(nodes,parent='') {
      for(const node of nodes) {
        const path=node.path.startsWith('/')?node.path:`${parent.replace(/\/$/,'')}/${node.path}`
        if(node.component==='question/question/index') return path
        const child=find(node.children||[],path)
        if(child) return child
      }
    }
    const questionPath=find(routers); assert.ok(questionPath)
    await page.goto(ui+questionPath)
    await page.locator('.el-select').filter({hasText:'全部审核状态'}).click()
    await page.getByRole('option',{name:'草稿',exact:true}).click()
    await page.getByRole('button',{name:'搜索',exact:true}).click()
    const questionRow=page.locator('.el-table__row').filter({has:page.getByText(q.title,{exact:true})})
    await questionRow.getByRole('button',{name:'查看',exact:true}).click()
    await page.getByRole('dialog',{name:'查看题目'}).waitFor()
    await page.waitForTimeout(400)
    assert.equal(await page.getByRole('dialog').getByRole('button',{name:'确 定',exact:true}).count(),0)
    await page.screenshot({path:'.runtime-release/screenshots/question-review-desktop.png',fullPage:true})
    assert.deepEqual(errors,[])
    console.log('PASS real desktop pages, read-only question viewer and equipment CSV download')
  } finally {await browser.close()}
}
await api(a,`/system/equipment/devices/${robot.id}?revision=${robot.revision+1}`,'DELETE')
await api(a,`/system/equipment/stations/${ws.id}?revision=${ws.revision}`,'DELETE')
console.log('PASS device release and station deletion; tests used restored database only')
