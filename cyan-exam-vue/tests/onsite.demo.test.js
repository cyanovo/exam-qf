import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const enabled = process.env.EXAM_ONSITE_DEMO_TEST === '1'
const base = 'http://127.0.0.1:8081'
const ui = 'http://127.0.0.1:5176'
const examId = Number(process.env.EXAM_ONSITE_DEMO_EXAM || 91001)
async function call(token, path, method = 'GET', body, success = true) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const result = await response.json()
  if (success) assert.equal(result.code, 200, `${method} ${path}: ${JSON.stringify(result)}`)
  else assert.notEqual(result.code, 200, `${method} ${path} must be rejected`)
  return result
}
async function login(username) {
  return (await call(null, '/login', 'POST', { username, password: 'Demo2026!Onsite' })).token
}

test('isolated demo: real onsite and result workflow', { skip: !enabled }, async () => {
  const owner = await login('demo_owner'), judge = await login('demo_judge')
  const reviewer = await login('demo_reviewer'), student = await login('demo_student')
  const exam = `/exam/onsite/${examId}`
  const results = `/exam/results/${examId}`
  const initial = (await call(owner, exam)).data
  assert.equal(initial.tasks.length, 0, 'Use a fresh isolated demo fixture; this test never resets existing scores')
  const body = { title: '程序讲解与现场演示', description: '展示程序运行结果，解释核心算法并回答提问。', durationMinutes: 15, judgeId: 91002,
    items: [{ title: '功能演示', maxScore: 20 }, { title: '讲解与答辩', maxScore: 10 }] }
  const id = (await call(owner, `${exam}/tasks`, 'POST', body)).data
  const task = `${exam}/tasks/${id}`, score = `${task}/scores/91004`
  await call(student, exam, 'GET', undefined, false)
  await call(owner, `${task}/publish`, 'POST', { reason: 'Demo publish' })
  await call(owner, score, 'PUT', { points: [18, 8], note: '', revision: 0, submit: true }, false)
  await call(judge, score, 'PUT', { points: [21, 8], note: '', revision: 0, submit: true }, false)
  await call(judge, score, 'PUT', { points: [18, null], note: 'Draft', revision: 0, submit: false })
  await call(judge, score, 'PUT', { points: [18, 8], note: '', revision: 0, submit: true }, false)
  await call(owner, `${task}/lock`, 'POST', { reason: 'Incomplete scoring' }, false)
  await call(judge, score, 'PUT', { points: [18, 8], note: 'Initial submission', revision: 1, submit: true })
  await call(judge, score, 'PUT', { points: [18, 8], note: '', revision: 2, submit: true }, false)
  await call(judge, `${score}/request`, 'POST', { reason: 'Correct explanation score' })
  await call(judge, `${score}/approve`, 'POST', { reason: 'Self approval denied' }, false)
  await call(owner, `${score}/approve`, 'POST', { reason: 'Independent approval' })
  await call(judge, score, 'PUT', { points: [18, 9], note: 'Corrected', revision: 4, submit: true })
  await call(owner, `${results}/reviewers`, 'POST', { userId: 91003 })
  await call(owner, `${results}/request`, 'POST', { reason: 'Not locked yet' }, false)
  await call(owner, `${task}/lock`, 'POST', { reason: 'All scores checked' })
  await call(owner, `${results}/request`, 'POST', { reason: 'Ready for review' })
  const result = (await call(owner, results)).data
  assert.equal(result.snapshot.maxScore, 40)
  assert.equal(result.snapshot.rows[0].total, 35)
  await call(owner, `${task}/reopen`, 'POST', { reason: 'Locked result' }, false)
  await call(reviewer, `${results}/review/approve`, 'POST', { reason: 'Verified independently' })
  await call(reviewer, `${results}/review/release`, 'POST', { reason: 'Publish verified results' })
  assert.equal((await call(student, `/exam/results/mine/${examId}`)).data.total, 35)
  const interactive = (await call(owner, '/exam/onsite/91002/tasks', 'POST', body)).data
  await call(owner, `/exam/onsite/91002/tasks/${interactive}/publish`, 'POST', { reason: 'Ready for interactive demo' })
  await call(owner, '/exam/results/91002/reviewers', 'POST', { userId: 91003 })
})

test('isolated demo: real judge UI, desktop and mobile', { skip: !enabled }, async t => {
  const require = createRequire(import.meta.url)
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
  const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addCookies([
    { name: 'Admin-Token', value: 'original-system-session', url: ui },
    { name: 'Exam-Token-127-0-0-1-8081', value: await login('demo_judge'), url: ui }
  ])
  const page = await context.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`${ui}/exam/onsite?examId=91002`)
  await page.getByText('Demo Student', { exact: true }).waitFor()
  await page.locator('.onsite > .el-loading-mask').waitFor({ state: 'hidden' })
  await page.screenshot({ path: 'tests/onsite-desktop.png', fullPage: true })
  await page.getByRole('button', { name: '评分', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('spinbutton', { name: '功能演示', exact: true }).fill('17')
  await dialog.getByRole('spinbutton', { name: '讲解与答辩', exact: true }).fill('8')
  await dialog.getByRole('button', { name: '保存草稿', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  await page.getByRole('tabpanel', { name: '考生评分' }).getByText('25', { exact: true }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'tests/onsite-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  await page.getByRole('button', { name: '评分', exact: true }).click()
  await dialog.waitFor({ state: 'visible' })
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'tests/onsite-score-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  assert.deepEqual(errors, [])
  assert.equal((await context.cookies()).find(c => c.name === 'Admin-Token').value, 'original-system-session')
})
