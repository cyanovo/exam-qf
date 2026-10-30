import assert from 'node:assert/strict'
import {readFileSync,mkdirSync} from 'node:fs'
import {createRequire} from 'node:module'

const cfg=JSON.parse(readFileSync(process.env.EXAM_VERIFY_CONFIG || new URL('./config.local.json',import.meta.url),'utf8'))
const url=`http://127.0.0.1:${cfg.WebPort}`
assert.ok(process.env.EXAM_VERIFY_PASSWORD,'Set EXAM_VERIFY_PASSWORD for a read-only administrator smoke test')
const health=await (await fetch(url+'/release-health')).json()
assert.equal(health.status,'UP')
const login=await (await fetch(url+'/prod-api/login',{
  method:'POST',headers:{'Content-Type':'application/json'},
  body:JSON.stringify({username:process.env.EXAM_VERIFY_USER||'admin',password:process.env.EXAM_VERIFY_PASSWORD})
})).json()
assert.equal(login.code,200)
for(const route of ['/getInfo','/getRouters','/system/equipment','/question/question/list?pageNum=1&pageSize=10']) {
  const result=await (await fetch(url+'/prod-api'+route,{headers:{Authorization:`Bearer ${login.token}`}})).json()
  assert.equal(result.code,200,route)
}
if(process.env.PLAYWRIGHT_MODULE_PATH) {
  const require=createRequire(import.meta.url)
  const {chromium}=require(process.env.PLAYWRIGHT_MODULE_PATH)
  const browser=await chromium.launch({channel:'msedge',headless:true})
  try {
    const context=await browser.newContext({viewport:{width:1440,height:960}})
    await context.addCookies([{name:'Admin-Token',value:login.token,url}])
    const page=await context.newPage(), errors=[]
    page.on('pageerror',e=>errors.push(e.message))
    await page.goto(url+'/system/equipment')
    await page.getByRole('heading',{name:'设备与工位'}).waitFor()
    await page.waitForTimeout(400)
    mkdirSync('.runtime-release/screenshots',{recursive:true})
    await page.screenshot({path:'.runtime-release/screenshots/live-equipment.png',fullPage:true})
    if(process.env.EXAM_VERIFY_USER_ORGANIZATION) {
      const tree=await (await fetch(url+'/prod-api/system/user/deptTree',{headers:{Authorization:`Bearer ${login.token}`}})).json()
      assert.equal(tree.code,200)
      assert.ok(JSON.stringify(tree.data).includes('考试中心'))
      assert.ok(!/若依科技|深圳总公司|长沙分公司/.test(JSON.stringify(tree.data)))
      await page.goto(url+'/system/user')
      await page.getByPlaceholder('搜索考务或考生分组').waitFor()
      await page.getByText('考试中心',{exact:true}).waitFor()
      await page.waitForTimeout(400)
      assert.ok(!/若依科技|深圳总公司|长沙分公司/.test(await page.locator('body').innerText()))
      await page.screenshot({path:'.runtime-release/screenshots/exam-user-management.png',fullPage:true})
      await page.getByRole('button',{name:'新增',exact:true}).click()
      const dialog=page.getByRole('dialog',{name:'添加用户'})
      await dialog.waitFor()
      await dialog.getByPlaceholder('请输入姓名').waitFor()
      await dialog.getByText('请选择考务或考生分组',{exact:true}).waitFor()
      const details=await (await fetch(url+'/prod-api/system/user/',{headers:{Authorization:`Bearer ${login.token}`}})).json()
      assert.equal(details.code,200)
      assert.ok(!/董事长|项目经理|人力资源|普通员工/.test(JSON.stringify(details.posts)))
      await page.waitForTimeout(400)
      await page.screenshot({path:'.runtime-release/screenshots/exam-user-add.png',fullPage:true})
      console.log('PASS exam group tree, user list, add-user labels and exam posts')
    }
    assert.deepEqual(errors,[])
  } finally {await browser.close()}
}
console.log(`PASS live read-only login, proxy, menus, question list and equipment page: ${health.release}`)
