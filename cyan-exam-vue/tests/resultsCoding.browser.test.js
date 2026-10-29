import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const baseURL = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5173'

async function fixture(t, path) {
  const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addCookies([{ name: 'Admin-Token', value: 'test-only', url: baseURL }])
  const page = await context.newPage()
  const state = { errors: [], submissions: [], resultState: 'REVIEW' }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/dev-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    let data = {}
    if (path === '/getInfo') {
      await route.fulfill({ json: { code: 200, user: { userId: 8, userName: 'reviewer' },
        roles: ['admin'], permissions: ['*:*:*'] } }); return
    }
    if (path === '/getRouters') data = []
    else if (path === '/exam/results/1') data = {
      examName: 'Python Exam', state: state.resultState, version: 1, reviewers: [{ userId: 8, name: 'Reviewer' }],
      snapshot: { statistics: { registered: 2, completed: 2, highest: 10, lowest: 10, average: 10 },
        rows: [{ userId: 2, studentName: 'Student A', total: 10, rank: 1, tied: true, scores: [{ questionId: 3, score: 10, maxScore: 10 }] }] },
      events: []
    }
    else if (path === '/exam/results/1/review/approve') state.resultState = 'APPROVED'
    else if (path === '/exam/results/1/review/release') state.resultState = 'RELEASED'
    else if (path === '/exam/results/mine') data = [{ examId: 1, examName: 'Python Exam' }]
    else if (path === '/exam/results/mine/1') data = { examName: 'Python Exam', version: 1, total: 10, rank: 1, tied: true, scores: [{ questionId: 3, score: 10, maxScore: 10 }] }
    else if (path === '/exam/student/1/session') data = { status: 'IN_PROGRESS' }
    else if (path === '/exam/student/questions') data = { examName: 'Python Exam', endTime: new Date(Date.now() + 600000).toISOString(),
      questions: [{ questionId: 3, questionType: 'PYTHON', title: 'Read a number and print it unchanged.',
        snapshotHash: 'snapshot', userAnswer: 'print(input())', samples: [{ input: '1\n', expected: '1\n' }] }] }
    else if (path === '/exam/code/1/history/3') data = state.submissions.map((body, index) => ({
      id: index + 1, mode: body.mode, state: 'SUCCEEDED', feedback: body.mode === 'SAMPLE' ? [{ case: 1, passed: true, status: 3, stdout: '1\n', stderr: '' }] : undefined
    }))
    else if (path === '/exam/code/1') {
      state.submissions.push(route.request().postDataJSON())
      data = { id: state.submissions.length, mode: state.submissions.at(-1).mode, state: 'QUEUED' }
    }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto(`${baseURL}${path}`)
  return { page, state }
}

test('results review transitions and responsive locked snapshot', async t => {
  const { page, state } = await fixture(t, '/exam/grading/results/1')
  await page.getByText('Student A').waitFor()
  await page.getByRole('button', { name: '通过复核', exact: true }).click()
  await page.locator('.el-message-box textarea').fill('independent review passed')
  await page.locator('.el-message-box').getByRole('button', { name: /确定|OK/ }).click()
  await page.getByRole('button', { name: '发布成绩', exact: true }).waitFor()
  await page.screenshot({ path: 'tests/results-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'tests/results-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  assert.equal(state.resultState, 'APPROVED')
  assert.deepEqual(state.errors, [])
})

test('published personal score page', async t => {
  const { page, state } = await fixture(t, '/exam/results')
  await page.getByRole('button', { name: '查看成绩' }).click()
  await page.getByText(/成绩版本 1/).waitFor()
  assert.deepEqual(state.errors, [])
})

test('Monaco mounts, sample and immutable formal requests remain distinct', async t => {
  const { page, state } = await fixture(t, '/exam/answer?examId=1')
  await page.locator('.monaco-editor').waitFor({ timeout: 60000 })
  await page.getByRole('button', { name: '运行样例', exact: true }).click()
  await page.getByText('已接收提交 1', { exact: true }).waitFor()
  assert.equal(state.submissions[0].mode, 'SAMPLE')
  assert.equal(state.submissions[0].source, 'print(input())')
  assert.equal(state.submissions[0].sourceHash.length, 64)
  await page.getByRole('button', { name: '正式提交', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: /确定|OK/ }).click()
  await page.getByText('已接收提交 2', { exact: true }).waitFor()
  assert.equal(state.submissions[1].mode, 'FORMAL')
  assert.notEqual(state.submissions[0].key, state.submissions[1].key)
  await page.getByRole('dialog').waitFor({ state: 'hidden' })
  await page.screenshot({ path: 'tests/coding-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'tests/coding-mobile.png', fullPage: true })
  assert.ok((await page.locator('.monaco-editor').boundingBox()).width >= 280)
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  assert.deepEqual(state.errors, [])
})
