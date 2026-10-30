import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'

const enabled = process.env.EXAM_ROLE_DEMO_TEST === '1'
const base = 'http://127.0.0.1:8182'
function sql(statement) {
  assert.ok(process.env.MYSQL_PWD, 'Explicit demo database credentials required')
  return execFileSync('D:/app/mysql-8.0.45-winx64/bin/mysql.exe',
    ['-u', 'root', '--default-character-set=utf8mb4', '-N', '-B',
      'cyan_exam_python_demo_20261029', '-e', statement], { encoding: 'utf8' }).trim()
}
async function api(token, path, method = 'GET', body, expected = 200) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const result = await response.json()
  if (expected === 200) assert.equal(result.code, 200, `${method} ${path}: ${JSON.stringify(result)}`)
  else assert.notEqual(result.code, 200, `${method} ${path} must be denied`)
  return result
}

test('real roles: CRUD, existing-session grant/revoke, status, menu tree and superadmin protection',
  { skip: !enabled, timeout: 60000 }, async () => {
    const stamp = Date.now()
    const accounts = [`ra_${stamp}`, `ru_${stamp}`]
    const userIds = []
    let roleId, admin, member
    try {
      for (const name of accounts) {
        userIds.push(Number(sql(`INSERT INTO sys_user(dept_id,user_name,nick_name,password,status,del_flag,pwd_update_date,create_by,create_time)
          SELECT dept_id,'${name}','Role Probe',password,'0','0',NOW(),'role-probe',NOW()
          FROM sys_user WHERE user_name='demo_owner'; SELECT LAST_INSERT_ID();`)))
      }
      const systemRole = Number(sql("SELECT role_id FROM sys_role WHERE role_key='system_admin' AND del_flag='0'"))
      sql(`INSERT INTO sys_user_role(user_id,role_id) VALUES(${userIds[0]},${systemRole}),(${userIds[1]},100)`)
      async function login(username) {
        return (await api(null, '/login', 'POST', { username, password: 'Demo2026!Onsite' })).token
      }
      admin = await login(accounts[0])
      member = await login(accounts[1])
      const roles = (await api(admin, '/system/role/list?pageSize=100')).rows
      for (const key of ['system_admin', 'teacher', 'student', 'judge', 'reviewer']) {
        assert.ok(roles.some(r => r.roleKey === key), `Missing business role ${key}`)
      }
      if (process.env.PLAYWRIGHT_MODULE_PATH) {
        const require = createRequire(import.meta.url)
        const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH)
        const browser = await chromium.launch({ channel: 'msedge', headless: true })
        try {
          const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
          const ui = 'http://127.0.0.1:5178'
          await context.addCookies([{ name: 'Exam-Token-127-0-0-1-8182', value: admin, url: ui }])
          const page = await context.newPage()
          const errors = []
          page.on('pageerror', e => errors.push(e.message))
          await page.goto(`${ui}/system/role`)
          await page.getByRole('cell', { name: '命题教师', exact: true }).waitFor()
          await page.screenshot({ path: 'tests/roles-real-desktop.png', fullPage: true })
          const teacherRow = page.locator('.el-table__row').filter({ has: page.getByRole('cell', { name: '命题教师', exact: true }) })
          await teacherRow.getByRole('button', { name: '成员', exact: true }).click()
          await page.getByRole('button', { name: '添加用户', exact: true }).waitFor()
          await page.getByRole('button', { name: '添加用户', exact: true }).click()
          await page.getByRole('dialog').last().waitFor()
          assert.equal(page.url(), `${ui}/system/role`)
          assert.deepEqual(errors, [])
        } finally { await browser.close() }
      }
      await api(member, '/system/role/list', 'GET', undefined, 403)
      await api(member, '/question/question/list', 'GET', undefined, 403)
      const teacherId = roles.find(r => r.roleKey === 'teacher').roleId
      const tree = await api(admin, `/system/menu/roleMenuTreeselect/${teacherId}`)
      function flatten(nodes) { return nodes.flatMap(n => [n.id, ...flatten(n.children || [])]) }
      assert.ok(flatten(tree.menus).includes(2000), 'Role administrator sees business permissions outside own menus')
      const roleKey = `probe_${stamp}`
      await api(admin, '/system/role', 'POST', {
        roleName: `Role Probe ${stamp}`, roleKey, roleSort: 90, status: '0',
        dataScope: '5', menuIds: [2000], menuCheckStrictly: true, deptCheckStrictly: true
      })
      roleId = Number(sql(`SELECT role_id FROM sys_role WHERE role_key='${roleKey}'`))
      await api(admin, `/system/role/authUser/selectAll?roleId=${roleId}&userIds=${userIds[1]}`, 'PUT')
      await api(member, '/question/question/list')
      assert.ok((await api(member, '/getInfo')).roles.includes(roleKey))
      assert.equal((await api(admin, `/system/role/authUser/allocatedList?roleId=${roleId}`)).rows.length, 1)
      await api(admin, '/system/role/changeStatus', 'PUT', { roleId, status: '1' })
      await api(member, '/question/question/list', 'GET', undefined, 403)
      assert.ok(!(await api(member, '/getInfo')).roles.includes(roleKey))
      await api(admin, '/system/role/changeStatus', 'PUT', { roleId, status: '0' })
      await api(member, '/question/question/list')
      const editedRole = {
        roleId, roleName: `Role Probe ${stamp}`, roleKey, roleSort: 90, status: '0',
        menuCheckStrictly: true, deptCheckStrictly: true
      }
      await api(admin, '/system/role', 'PUT', { ...editedRole, menuIds: [] })
      await api(member, '/question/question/list', 'GET', undefined, 403)
      await api(admin, '/system/role', 'PUT', { ...editedRole, menuIds: [2000] })
      await api(member, '/question/question/list')
      await api(admin, '/system/role/authUser/cancel', 'PUT', { roleId, userId: userIds[1] })
      await api(member, '/question/question/list', 'GET', undefined, 403)
      await api(admin, `/system/role/authUser/selectAll?roleId=1&userIds=${userIds[1]}`, 'PUT', undefined, 403)
      await api(admin, '/system/role/changeStatus', 'PUT', { roleId: 1, status: '1' }, 403)
      await api(admin, '/system/role', 'PUT', {
        roleId, roleName: `Updated ${stamp}`, roleKey, roleSort: 90, status: '0',
        menuIds: [], menuCheckStrictly: true, deptCheckStrictly: true
      })
      assert.equal((await api(admin, `/system/role/${roleId}`)).data.roleName, `Updated ${stamp}`)
      await api(admin, `/system/role/${roleId}`, 'DELETE')
    } finally {
      for (const token of [admin, member].filter(Boolean)) {
        await api(token, '/logout', 'POST').catch(() => {})
      }
      if (roleId) sql(`DELETE FROM sys_role_menu WHERE role_id=${roleId};
        DELETE FROM sys_role_dept WHERE role_id=${roleId};
        DELETE FROM sys_user_role WHERE role_id=${roleId};
        DELETE FROM sys_role WHERE role_id=${roleId} AND create_by='${accounts[0]}';`)
      for (let i = 0; i < userIds.length; i++) {
        sql(`DELETE FROM sys_user_role WHERE user_id=${userIds[i]};
          DELETE FROM sys_user WHERE user_id=${userIds[i]} AND user_name='${accounts[i]}' AND create_by='role-probe';`)
      }
    }
  })
