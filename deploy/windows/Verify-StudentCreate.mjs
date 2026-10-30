import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const cfg = JSON.parse(readFileSync('.runtime-release/test-config.json', 'utf8'))
assert.ok(JSON.stringify(cfg).includes('cyan_restore_'), 'Only run against the restored test database')
const url = `http://127.0.0.1:${cfg.WebPort}`
const login = await (await fetch(url + '/prod-api/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: process.env.EXAM_VERIFY_PASSWORD })
})).json()
assert.equal(login.code, 200)
async function api(path, body, method = body ? 'POST' : 'GET') {
  return (await fetch(url + '/prod-api' + path, {
    method,
    headers: { Authorization: `Bearer ${login.token}`, 'Content-Type': 'application/json' },
    ...(body ? { body: JSON.stringify(body) } : {})
  })).json()
}
const historical = await api('/system/student/import', {
  prefix: 'STU', startNo: 1, count: 2, password: 'Unused123456!'
})
assert.equal(historical.code, 200)
assert.match(historical.msg, /成功创建 0/)
assert.match(historical.msg, /跳过 2/)

const prefix = `B${Date.now().toString(36)}`
const request = { prefix, startNo: 1, count: 2, password: 'BatchTest123!', className: 'batch-regression' }
const created = await api('/system/student/import', request)
assert.equal(created.code, 200)
assert.match(created.msg, /成功创建 2/)
const repeated = await api('/system/student/import', { ...request, password: 'DoNotReplace123!' })
assert.equal(repeated.code, 200)
assert.match(repeated.msg, /成功创建 0/)
const studentLogin = await (await fetch(url + '/prod-api/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: prefix + '1', password: request.password })
})).json()
assert.equal(studentLogin.code, 200, 'Existing password must remain unchanged')
const single = await api('/system/student', { studentNo: 'STU1', nickName: 'must-not-create' })
assert.notEqual(single.code, 200)
assert.ok(!single.msg.includes('SQL'))
const invalid = await api('/system/student/import', { ...request, count: 201 })
assert.notEqual(invalid.code, 200)
console.log('PASS historical numbers, new batch, retry, password preservation, single-create conflict, count limit')

const users = await api('/system/user/list?userName=' + prefix)
assert.equal(users.code, 200)
assert.equal(users.rows.length, 2)
assert.ok(users.rows.every(user => user.deptId === 102), 'Batch students belong to student group')
const id = users.rows[0].userId
const before = await api('/system/user/' + id)
const edited = {
  userId: id, userName: before.data.userName, nickName: before.data.nickName,
  deptId: 108, roleIds: before.roleIds, postIds: before.postIds,
  phonenumber: '139' + String(Date.now()).slice(-8), email: prefix + '@example.test'
}
assert.equal((await api('/system/user', edited, 'PUT')).code, 200)
edited.deptId = 102
assert.equal((await api('/system/user', edited, 'PUT')).code, 200, 'Unchanged name, phone and email permit group changes')
const after = await api('/system/user/' + id)
assert.equal(after.data.deptId, 102)
assert.deepEqual(after.roleIds, before.roleIds)
const collision = await api('/system/user', { ...edited, userName: users.rows[1].userName }, 'PUT')
assert.notEqual(collision.code, 200, 'Another account name is still rejected')
const added = await api('/system/student', { studentNo: prefix + '3', nickName: 'single-student' })
assert.equal(added.code, 200)
const singleUser = await api('/system/user/list?userName=' + prefix + '3')
assert.equal(singleUser.rows[0].deptId, 102)
console.log('PASS batch/single default group, group edits, unchanged contact fields, role preservation and real name collision')

if (process.env.PLAYWRIGHT_MODULE_PATH) {
  const require = createRequire(import.meta.url)
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH)
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
    await context.addCookies([{ name: 'Admin-Token', value: login.token, url }])
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto(url + '/student')
    await page.getByRole('button', { name: '一键创建', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '一键创建学生账号' })
    await dialog.getByRole('spinbutton').nth(1).fill('2')
    await dialog.getByRole('button', { name: '开始创建' }).click()
    await dialog.waitFor({ state: 'hidden' })
    await page.getByText(/成功创建 0 名学生/).waitFor()
    await page.screenshot({ path: '.runtime-release/screenshots/student-batch-result.png', fullPage: true })
    assert.deepEqual(errors, [])
    console.log('PASS student batch dialog, historical-number result and list refresh')
  } finally {
    await browser.close()
  }
}
