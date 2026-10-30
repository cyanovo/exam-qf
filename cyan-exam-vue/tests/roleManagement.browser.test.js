import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { isStudentOnly } from '../src/utils/examIdentity.js'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

test('student-only identity respects multiple roles and actual permissions', () => {
  assert.equal(isStudentOnly({ roles: ['student'], permissions: [] }), true)
  assert.equal(isStudentOnly({ roles: ['student', 'teacher'], permissions: ['question:question:list'] }), false)
  assert.equal(isStudentOnly({ roles: ['student', 'judge'], permissions: ['exam:onsite:score'] }), false)
  assert.equal(isStudentOnly({ roles: ['student', 'judge'], permissions: [] }), true)
})

async function fixture(t) {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
  await context.addCookies([{ name: 'Exam-Token-127-0-0-1-8182', value: 'role-test', url: base }])
  const page = await context.newPage()
  const state = { members: [], writes: [], errors: [] }
  const roles = [
    { roleId: 1, roleName: '超级管理员', roleKey: 'admin', status: '0', remark: '内置管理身份' },
    { roleId: 21, roleName: '系统管理员', roleKey: 'system_admin', status: '0', remark: '账号与角色授权' },
    { roleId: 22, roleName: '命题教师', roleKey: 'teacher', status: '0', remark: '题库维护与考试编排' },
    { roleId: 100, roleName: '考生', roleKey: 'student', status: '0', remark: '参加考试' },
    { roleId: 23, roleName: '裁判', roleKey: 'judge', status: '0', remark: '公共评分池现场评分' },
    { roleId: 24, roleName: '成绩复核员', roleKey: 'reviewer', status: '0', remark: '复核与发布成绩' }
  ]
  page.on('pageerror', e => state.errors.push(e.message))
  await page.route('**/dev-api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname.replace('/dev-api', '')
    let json = { code: 200, data: [] }
    if (path === '/getInfo') json = { code: 200, user: { userId: 1, userName: 'admin' }, roles: ['admin'], permissions: ['*:*:*'] }
    else if (path === '/getRouters') json.data = [{ path: '/system', component: 'Layout', meta: { title: '系统管理' },
      children: [{ path: 'role', component: 'system/role/index', meta: { title: '角色管理' } }] }]
    else if (path === '/system/role/list') json = { code: 200, rows: roles.map(r => ({ ...r, memberCount: state.members.length })), total: roles.length }
    else if (path === '/system/role/authUser/allocatedList') json = { code: 200, rows: state.members, total: state.members.length }
    else if (path === '/system/role/authUser/unallocatedList') json = { code: 200, rows: [{ userId: 7, userName: 'teacher1', nickName: '教师甲', status: '0' }], total: 1 }
    else if (path === '/system/role/authUser/selectAll') {
      state.members = [{ userId: 7, userName: 'teacher1', nickName: '教师甲', status: '0' }]
      state.writes.push(path)
    } else if (path === '/system/role/authUser/cancel') {
      assert.equal(req.postDataJSON().roleId, 22)
      state.members = []
      state.writes.push(path)
    } else if (path === '/system/role/changeStatus') {
      const body = req.postDataJSON()
      roles.find(r => r.roleId === body.roleId).status = body.status
      state.writes.push(path)
    } else if (path.startsWith('/system/dict/data/type/')) json.data = [
      { dictLabel: '正常', dictValue: '0' }, { dictLabel: '停用', dictValue: '1' }
    ]
    await route.fulfill({ json })
  })
  await page.goto(`${base}/system/role`)
  await page.getByRole('heading', { name: '角色管理', exact: true }).waitFor()
  await page.getByRole('cell', { name: '命题教师', exact: true }).waitFor()
  return { page, state }
}

test('business roles and member assignment stay in one page; protected superadmin', async t => {
  const { page, state } = await fixture(t)
  const teacher = page.getByRole('row').filter({ has: page.getByRole('cell', { name: '命题教师', exact: true }) })
  const superAdmin = page.getByRole('row').filter({ has: page.getByRole('cell', { name: '超级管理员', exact: true }) })
  assert.equal(await superAdmin.getByRole('switch').isDisabled(), true)
  assert.equal(await superAdmin.getByRole('button', { name: '成员', exact: true }).count(), 0)
  await teacher.getByRole('button', { name: '成员', exact: true }).click()
  const drawer = page.locator('.el-drawer')
  await drawer.getByRole('button', { name: '添加用户', exact: true }).click()
  const dialog = page.locator('.el-dialog')
  await dialog.getByRole('cell', { name: 'teacher1', exact: true }).click()
  await dialog.getByRole('button', { name: '确 定', exact: true }).click()
  await drawer.getByRole('cell', { name: '教师甲', exact: true }).waitFor()
  assert.ok(page.url().endsWith('/system/role'))
  await drawer.getByRole('button', { name: '取消授权', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
  await drawer.getByText('暂无数据', { exact: true }).waitFor()
  assert.equal(state.members.length, 0)
  await drawer.locator('.el-drawer__close-btn').click()
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'tests/roles-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'tests/roles-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  assert.equal(state.writes.length, 2)
  assert.deepEqual(state.errors, [])
})

test('role can be disabled and cancel preserves existing state', async t => {
  const { page, state } = await fixture(t)
  const teacher = page.getByRole('row').filter({ has: page.getByRole('cell', { name: '命题教师', exact: true }) })
  await teacher.locator('.el-switch').click()
  await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(await teacher.getByRole('switch').getAttribute('aria-checked'), 'true')
  await teacher.locator('.el-switch').click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
  await page.getByText('停用成功', { exact: true }).waitFor()
  assert.equal(state.writes.length, 1)
  assert.deepEqual(state.errors, [])
})
