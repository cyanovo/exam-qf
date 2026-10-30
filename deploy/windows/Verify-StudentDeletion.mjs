import assert from 'node:assert/strict'
import { readFileSync, mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { createRequire } from 'node:module'

const cfg = JSON.parse(readFileSync('.runtime-release/test-config.json', 'utf8'))
assert.ok(cfg.Database.startsWith('cyan_restore_'), 'Test database only')
const url = `http://127.0.0.1:${cfg.WebPort}`
function sql(text) {
  return execFileSync(cfg.Mysql, ['--protocol=TCP', `--host=${cfg.DatabaseHost}`,
    `--port=${cfg.DatabasePort}`, `--user=${cfg.DatabaseUser}`, '--default-character-set=utf8mb4',
    '-N', '-B', cfg.Database, '-e', text],
  { encoding: 'utf8', env: { ...process.env, MYSQL_PWD: cfg.DatabasePassword } }).trim()
}
let token
async function api(path, method = 'GET', body, ok = true, auth = token) {
  const result = await (await fetch(url + '/prod-api' + path, {
    method, headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${auth}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  })).json()
  if (ok) assert.equal(result.code, 200, `${method} ${path}: ${JSON.stringify(result)}`)
  else assert.notEqual(result.code, 200, `${method} ${path} must reject`)
  return result
}
token = (await api('/login', 'POST', { username: 'admin', password: process.env.EXAM_VERIFY_PASSWORD })).token
const prefix = `D${Date.now().toString(36)}`
const batch = { prefix, startNo: 1, count: 6, password: 'DeleteTest123!' }
await Promise.all(['P', 'Q', 'R'].map(suffix =>
  api('/system/student/import', 'POST', { ...batch, prefix: prefix + suffix, count: 2 })))
console.log('PASS three concurrent batches with distinct new numbers')
await api('/system/student/import', 'POST', batch)
const students = (await api('/system/student/list?pageSize=100&studentNo=' + prefix)).rows
  .filter(s => /^[1-6]$/.test(s.studentNo.slice(prefix.length)))
assert.equal(students.length, 6)
const byNo = n => students.find(s => s.studentNo === prefix + n)

const empty = byNo(1)
const oldToken = (await api('/login', 'POST', { username: empty.studentNo, password: batch.password })).token
sql(`INSERT INTO sys_user_post(user_id,post_id) VALUES(${empty.userId},4)`)
await api('/system/student/' + empty.studentId, 'DELETE')
assert.equal(sql(`SELECT COUNT(*) FROM cyan_student WHERE user_id=${empty.userId}`), '0')
assert.equal(sql(`SELECT COUNT(*) FROM sys_user_role WHERE user_id=${empty.userId}`), '0')
assert.equal(sql(`SELECT COUNT(*) FROM sys_user_post WHERE user_id=${empty.userId}`), '0')
await api('/getInfo', 'GET', undefined, false, oldToken)
await api('/system/student/import', 'POST', { ...batch, count: 1 })
const replacement = (await api('/system/student/list?studentNo=' + empty.studentNo)).rows[0]
assert.notEqual(replacement.userId, empty.userId)
assert.notEqual(replacement.studentId, empty.studentId)

const userEntry = byNo(2)
await api('/system/user/' + userEntry.userId, 'DELETE')
assert.equal(sql(`SELECT COUNT(*) FROM cyan_student WHERE user_id=${userEntry.userId}`), '0')
await api('/system/student', 'POST', { studentNo: userEntry.studentNo, nickName: 'recreated' })
assert.notEqual((await api('/system/student/list?studentNo=' + userEntry.studentNo)).rows[0].userId, userEntry.userId)

const legacy = byNo(3)
sql(`UPDATE sys_user SET del_flag='2' WHERE user_id=${legacy.userId}`)
await api('/system/student/import', 'POST', { ...batch, startNo: 3, count: 1 })
assert.equal(sql(`SELECT COUNT(*) FROM cyan_student WHERE user_id=${legacy.userId}`), '0')

const history = byNo(4)
const examId = Number(sql(`INSERT INTO exam_info(exam_name,start_time,end_time)
  VALUES('deletion-history-${prefix}',NOW()-INTERVAL 2 HOUR,NOW()-INTERVAL 1 HOUR); SELECT LAST_INSERT_ID();`))
sql(`INSERT INTO exam_student(exam_id,user_id) VALUES(${examId},${history.userId});
  INSERT INTO exam_answer_record(exam_id,user_id,question_id,answer,score,graded_at)
  VALUES(${examId},${history.userId},999999,'retained',7,NOW());`)
await api('/system/student/' + history.studentId, 'DELETE')
assert.equal(sql(`SELECT student_no FROM cyan_student WHERE user_id=${history.userId}`), history.studentNo)
assert.equal(sql(`SELECT score FROM exam_answer_record WHERE exam_id=${examId} AND user_id=${history.userId}`), '7')
assert.equal((await api('/system/student/list?studentNo=' + history.studentNo)).total, 0)
const skipped = await api('/system/student/import', 'POST', { ...batch, startNo: 4, count: 1 })
assert.match(skipped.msg, /成功创建 0/)
await api('/system/student', 'POST', { studentNo: history.studentNo, nickName: 'must-not-reassign' }, false)

// A published snapshot alone must preserve the original identity too.
const snapshot = byNo(5)
sql(`INSERT INTO exam_result_version(exam_id,version_no,state,payload,requested_by,reason)
 VALUES(${examId},1,'RELEASED','{"rows":[{"userId":${snapshot.userId},"total":9}]}',1,'deletion verification')`)
await api('/system/user/' + snapshot.userId, 'DELETE')
assert.equal(sql(`SELECT COUNT(*) FROM cyan_student WHERE user_id=${snapshot.userId}`), '1')
assert.equal((await api('/system/student/list?studentNo=' + snapshot.studentNo)).total, 0)
const retained = await api('/system/student/import', 'POST', { ...batch, startNo: 5, count: 1 })
assert.match(retained.msg, /成功创建 0/)

// A rejected mixed batch must leave the first target untouched.
const protectedBatch = byNo(6)
await api(`/system/user/${protectedBatch.userId},1`, 'DELETE', undefined, false)
assert.equal(sql(`SELECT del_flag FROM sys_user WHERE user_id=${protectedBatch.userId}`), '0')
assert.equal(sql(`SELECT COUNT(*) FROM cyan_student WHERE user_id=${protectedBatch.userId}`), '1')
console.log('PASS both delete entries, number reuse with new IDs, legacy cleanup, token revocation, history/snapshot retention and rejected batch atomicity')

if (process.env.PLAYWRIGHT_MODULE_PATH) {
  const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH)
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
    await context.addCookies([{ name: 'Admin-Token', value: token, url }])
    const page = await context.newPage()
    await page.goto(url + '/student')
    await page.getByPlaceholder('请输入学号', { exact: true }).fill(protectedBatch.studentNo)
    await page.getByRole('button', { name: '搜索', exact: true }).click()
    const row = page.getByRole('row').filter({ hasText: protectedBatch.studentNo })
    await row.getByRole('button', { name: '删除', exact: true }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByText(/无考试记录的学号会释放/).waitFor()
    await page.waitForTimeout(400)
    mkdirSync('.runtime-release/screenshots', { recursive: true })
    await page.screenshot({ path: '.runtime-release/screenshots/student-delete-confirm.png', fullPage: true })
    await dialog.getByRole('button', { name: '确定', exact: true }).click()
    await page.getByText('删除成功，无考试记录的学号已释放；历史考试记录保留', { exact: true }).waitFor()
    assert.equal(sql(`SELECT COUNT(*) FROM cyan_student WHERE user_id=${protectedBatch.userId}`), '0')
    console.log('PASS delete dialog, student number display, one confirmation and list refresh')
  } finally { await browser.close() }
}
