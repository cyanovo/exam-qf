import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const baseURL = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5173'

async function fixture(t, path) {
  const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await context.addCookies([{ name: 'Admin-Token', value: 'test-only', url: baseURL }])
  const page = await context.newPage()
  const state = { submissions: [], errors: [] }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/dev-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    let response = { code: 200, data: {} }
    if (path === '/getInfo') {
      response = { code: 200, user: { userId: 9, userName: 'teacher' },
        roles: ['teacher'], permissions: ['exam:grading:list', 'exam:grading:detail', 'exam:grading:edit'] }
    } else if (path === '/getRouters') {
      response.data = []
    } else if (path === '/exam/grading/detail/1/2') {
      response.data = {
        examName: 'Theory', studentName: 'Student', sessionStatus: 'SUBMITTED',
        questions: [
          { questionId: 1, recordId: 1, title: 'Multi choice', type: 'MULTI_CHOICE',
            studentAnswer: 'B,A', referenceAnswer: 'A,B', score: 10, maxScore: 10 },
          { questionId: 2, recordId: 2, title: 'Short answer', type: 'SHORT_ANSWER',
            studentAnswer: 'reference', referenceAnswer: 'reference', score: null, maxScore: 10 }
        ],
        revisions: [{ questionId: 1, previousScore: null, newScore: 10, graderId: 0, reason: 'auto v1' }]
      }
    } else if (path === '/exam/grading/students/1') {
      response.data = [{ userId: 2, studentName: 'Student', answered: 2, total: 3,
        graded: 2, pending: 1, score: 10, maxScore: 30, gradingComplete: false, sessionStatus: 'SUBMITTED' }]
    } else if (path === '/exam/grading/submitScore') {
      state.submissions.push(route.request().postDataJSON())
    }
    await route.fulfill({ json: response })
  })
  await page.goto(`${baseURL}${path}`)
  await page.getByText('Student', { exact: false }).first().waitFor()
  return { page, state }
}

test('server grades are displayed, manual blanks are not silently zeroed, corrections need a reason', async t => {
  const { page, state } = await fixture(t, '/exam/grading/detail/1/2')
  const inputs = page.locator('.el-input-number input')
  assert.equal(await inputs.nth(0).inputValue(), '10')
  assert.equal(await inputs.nth(1).inputValue(), '')
  await page.getByText('系统自动判分', { exact: true }).waitFor()
  await inputs.nth(1).fill('6')
  await inputs.nth(1).blur()
  await page.getByRole('button', { name: '保存批改' }).click()
  await page.getByText('批改已保存', { exact: true }).waitFor()
  assert.deepEqual(state.submissions[0].scores, [{ recordId: 1, score: 10 }, { recordId: 2, score: 6 }])
  await inputs.nth(0).fill('8')
  await inputs.nth(0).blur()
  const reason = page.locator('textarea')
  await reason.waitFor()
  assert.equal(await page.getByRole('button', { name: '保存批改' }).isDisabled(), true)
  await reason.fill('review correction')
  assert.equal(await page.getByRole('button', { name: '保存批改' }).isDisabled(), false)
  await page.screenshot({ path: 'tests/grading-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.main-container')).marginLeft === '0px')
  await page.waitForTimeout(350)
  assert.ok(await page.locator('.sidebar-container').evaluate(el => el.getBoundingClientRect().right <= 1))
  assert.ok(await page.locator('.detail-header h2').evaluate(el => el.getBoundingClientRect().left >= 0))
  await page.screenshot({ path: 'tests/grading-mobile.png', fullPage: true })
  assert.deepEqual(state.errors, [])
})

test('progress distinguishes partial subtotal from completed grading and can refresh', async t => {
  const { page, state } = await fixture(t, '/exam/grading/students/1')
  await page.getByText('10 / 30', { exact: true }).waitFor()
  await page.getByText('待完成', { exact: true }).waitFor()
  await page.getByRole('button', { name: '刷新成绩' }).click()
  await page.getByText('10 / 30', { exact: true }).waitFor()
  assert.deepEqual(state.errors, [])
})
