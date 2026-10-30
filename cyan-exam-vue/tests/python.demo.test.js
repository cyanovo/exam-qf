import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash, randomUUID } from 'node:crypto'
import { createRequire } from 'node:module'

const enabled = process.env.EXAM_PYTHON_DEMO_TEST === '1'
const base = process.env.EXAM_PYTHON_API_URL || 'http://127.0.0.1:8181'
const ui = process.env.EXAM_PYTHON_UI_URL || 'http://127.0.0.1:5177'
const cookieName = `Exam-Token-${new URL(base).hostname.replaceAll('.', '-')}-${new URL(base).port}`
const examId = Number(process.env.EXAM_PYTHON_DEMO_EXAM || 92001)
const interactiveExamId = Number(process.env.EXAM_PYTHON_INTERACTIVE_EXAM || 92012)
const hash = value => createHash('sha256').update(value).digest('hex')
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function call(token, path, method = 'GET', body, success = true) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const result = await response.json()
  if (success) assert.equal(result.code, 200, `${path}: ${JSON.stringify(result)}`)
  else assert.notEqual(result.code, 200, `${path} should be denied`)
  return result
}
async function login(username) {
  return (await call(null, '/login', 'POST', { username, password: 'Demo2026!Onsite' })).token
}
async function until(fn, predicate, timeout = 90000) {
  const end = Date.now() + timeout
  while (Date.now() < end) {
    const result = await fn()
    if (predicate(result)) return result
    await sleep(1500)
  }
  assert.fail('Timed out waiting for actual backend evaluation')
}

test('real Python, objective rules and versioned regrade', { skip: !enabled }, async () => {
  const student = await login('demo_student'), owner = await login('demo_owner'), reviewer = await login('demo_reviewer')
  const questions = (await call(student, `/exam/student/questions?examId=${examId}`)).data
  const python = questions.questions.find(q => q.questionId === 92001)
  assert.ok(python, JSON.stringify(questions))
  assert.equal(python.answer, undefined)
  assert.equal(python.gradingRules, undefined)
  const submit = async (mode, source) => {
    const response = (await call(student, `/exam/code/${examId}`, 'POST', {
      questionId: 92001, mode, source, key: randomUUID(), snapshotHash: python.snapshotHash, sourceHash: hash(source)
    })).data
    const done = (await until(() => call(student, `/exam/code/submission/${response.id}`),
      result => ['SUCCEEDED', 'FAILED'].includes(result.data.state))).data
    assert.equal(done.state, 'SUCCEEDED', 'Student errors must complete rather than become infrastructure failures')
    if (mode === 'FORMAL') {
      assert.equal(done.feedback, undefined)
      assert.equal(done.score, undefined)
    }
    return done
  }
  const sample = await submit('SAMPLE', 'print(int(input()) * 2)')
  assert.equal(sample.feedback[0].stdout, '2\n')
  assert.equal(sample.feedback[0].passed, true)
  const error = await submit('SAMPLE', 'raise ValueError("demo")')
  assert.equal(error.feedback[0].status, 11)
  await sleep(10000)
  const timeout = await submit('SAMPLE', 'while True: pass')
  assert.equal(timeout.feedback[0].status, 5)
  const partial = await submit('FORMAL', 'n=int(input())\nprint(4 if n == 2 else 0)')
  await sleep(10000)
  const correct = await submit('FORMAL', 'print(int(input()) * 2)')
  await call(student, '/exam/student/submitAnswer', 'POST', { examId, questionId: 92002, answer: 'A' })
  await call(student, '/exam/student/submitAnswer', 'POST', { examId, questionId: 92003, answer: 'PY;wrong' })
  await call(student, `/exam/student/${examId}/finish`, 'POST')
  const detailPath = `/exam/grading/detail/${examId}/91004`
  const graded = (await until(() => call(owner, detailPath),
    result => result.data.questions.every(q => q.score != null))).data
  assert.deepEqual(graded.questions.map(q => q.score), [10, 3, 2])
  const historyPath = `/exam/code/${examId}/student/91004/history/92001`
  const history = (await call(owner, historyPath)).data
  assert.equal(history.find(row => row.id === partial.id).score, 4)
  assert.equal(history.find(row => row.id === correct.id).score, 10)
  await call(student, historyPath, 'GET', undefined, false)
  await call(student, `/exam/code/submission/${correct.id}/regrade`, 'POST', { version: 1, reason: 'Denied' }, false)
  await call(owner, `/exam/code/submission/${correct.id}/regrade`, 'POST', { version: 1, reason: '' }, false)
  await call(owner, `/exam/code/submission/${correct.id}/regrade`, 'POST', { version: 1, reason: 'Verify actual sandbox regrade' })
  await call(owner, `/exam/code/submission/${correct.id}/regrade`, 'POST', { version: 1, reason: 'Stale version' }, false)
  const regraded = (await until(() => call(owner, historyPath),
    result => result.data.find(row => row.id === correct.id)?.state === 'SUCCEEDED')).data.find(row => row.id === correct.id)
  assert.equal(regraded.runVersion, 2)
  assert.equal(regraded.evaluations.length, 1)
  assert.equal(regraded.evaluations[0].score, 10)
  assert.equal(regraded.evaluations[0].reason, 'Verify actual sandbox regrade')
  await until(() => call(owner, detailPath), result => result.data.questions[0].score === 10)
  await call(owner, `/exam/code/submission/${partial.id}/regrade`, 'POST', { version: 1, reason: 'Earlier submission must not replace final score' })
  assert.equal((await call(owner, detailPath)).data.questions[0].score, 10)
  await until(() => call(owner, historyPath), result => result.data.find(row => row.id === partial.id)?.state === 'SUCCEEDED')
})

// Run after the synthetic exam closes; student submission does not close an exam.
test('real publication and regrade lock after exam closure',
  { skip: process.env.EXAM_PYTHON_PUBLICATION_TEST !== '1' }, async () => {
  const student = await login('demo_student'), owner = await login('demo_owner'), reviewer = await login('demo_reviewer')
  const history = (await call(owner, `/exam/code/${examId}/student/91004/history/92001`)).data
  const correct = history.filter(row => row.mode === 'FORMAL').sort((a, b) => b.id - a.id)[0]
  assert.ok(correct)
  const results = `/exam/results/${examId}`
  await call(owner, `${results}/reviewers`, 'POST', { userId: 91003 })
  await call(owner, `${results}/request`, 'POST', { reason: 'Verified actual execution and grading rules' })
  await call(owner, `/exam/code/submission/${correct.id}/regrade`, 'POST', { version: 2, reason: 'Must be locked' }, false)
  await call(reviewer, `${results}/review/approve`, 'POST', { reason: 'Independent verification' })
  await call(reviewer, `${results}/review/release`, 'POST', { reason: 'Demo release' })
  assert.equal((await call(student, `/exam/results/mine/${examId}`)).data.total, 15)
})

test('real Python UI and history render at desktop/mobile sizes', { skip: process.env.EXAM_PYTHON_UI_TEST !== '1' }, async t => {
  const require = createRequire(import.meta.url)
  const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
  const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER || 'msedge' })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addCookies([{ name: cookieName, value: await login('demo_owner'), url: ui }])
  const page = await context.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto(`${ui}/exam/grading/detail/${examId}/91004`)
  await page.getByRole('button', { name: '评测记录', exact: true }).click()
  await page.getByRole('dialog').waitFor()
  await page.locator('.evaluation-dialog .el-table__expand-icon').first().click()
  await page.getByText('当前版本', { exact: false }).waitFor()
  await page.screenshot({ path: 'tests/python-history-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(300)
  await page.screenshot({ path: 'tests/python-history-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  await context.clearCookies()
  await context.addCookies([{ name: cookieName, value: await login('demo_student'), url: ui }])
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`${ui}/exam/answer?examId=${interactiveExamId}`)
  await page.getByText('Read an integer and print twice its value.', { exact: false }).waitFor()
  await page.screenshot({ path: 'tests/python-student-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(300)
  await page.screenshot({ path: 'tests/python-student-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  assert.deepEqual(errors, [])
})
