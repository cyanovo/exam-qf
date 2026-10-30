import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const baseURL = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5174'

test('regrade reason/version, history and mobile layout (mock API)', async t => {
  const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER || 'msedge' })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addCookies([{ name: 'Admin-Token', value: 'test-only', url: baseURL }])
  const page = await context.newPage(), requests = [], errors = []
  let version = 1
  page.on('pageerror', error => errors.push(error.message))
  await page.route('**/dev-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    let data = {}
    if (path === '/getInfo') {
      await route.fulfill({ json: { code: 200, user: { userId: 9, userName: 'teacher' },
        roles: ['teacher'], permissions: ['exam:grading:detail', 'exam:grading:edit'] } })
      return
    }
    if (path === '/getRouters') data = []
    else if (path === '/exam/grading/detail/1/2') data = {
      examName: 'Python automatic grading', studentName: 'Student', sessionStatus: 'SUBMITTED',
      questions: [{ questionId: 3, recordId: 3, type: 'PYTHON', title: 'Double the input integer',
        referenceAnswer: '{"hidden":"do not render raw config"}', studentAnswer: 'print(int(input()) * 2)',
        formalSubmissionId: 7, runVersion: version, evaluationState: 'SUCCEEDED', score: 10, maxScore: 10 }]
    }
    else if (path === '/exam/code/1/student/2/history/3') data = [{
      id: 7, mode: 'FORMAL', runVersion: version, state: 'SUCCEEDED', score: 10, source: 'print(int(input()) * 2)',
      snapshotHash: 'a'.repeat(64),
      feedback: JSON.stringify([{ case: 1, passed: true, verdict: 'Accepted', score: 10, weight: 10, runtime: 'go-judge:1.13.3/python-3.12.14' }]),
      evaluations: version > 1 ? [{ runVersion: 1, state: 'SUCCEEDED', score: 10, actorId: 9, reason: 'Review sandbox result', feedback: '[]' }] : []
    }]
    else if (path === '/exam/code/submission/7/regrade') {
      requests.push(route.request().postDataJSON())
      version++
    }
    await route.fulfill({ json: { code: 200, data } })
  })
  await page.goto(`${baseURL}/exam/grading/detail/1/2`)
  await page.getByRole('button', { name: '重新评测', exact: true }).click()
  const prompt = page.locator('.el-message-box')
  await prompt.getByRole('button', { name: /确定|OK/ }).click()
  await page.getByText('请输入1至500字的原因', { exact: true }).waitFor()
  assert.equal(requests.length, 0)
  await prompt.locator('textarea').fill('Review sandbox result')
  await prompt.getByRole('button', { name: /确定|OK/ }).click()
  await page.getByText('已重新加入评测队列', { exact: true }).waitFor()
  assert.deepEqual(requests, [{ version: 1, reason: 'Review sandbox result' }])
  await page.getByRole('button', { name: '评测记录', exact: true }).click()
  await page.locator('.evaluation-dialog .el-table__expand-icon').first().click()
  await page.getByText('Review sandbox result', { exact: true }).waitFor()
  await page.screenshot({ path: 'tests/regrade-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: 'tests/regrade-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  assert.deepEqual(errors, [])
})
