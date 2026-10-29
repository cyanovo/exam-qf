import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const baseURL = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5173'

async function fixture(t, options = {}) {
  const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await context.addCookies([{ name: 'Admin-Token', value: 'test-only', url: baseURL }])
  const page = await context.newPage()
  const state = { saves: [], finishes: 0, failSave: false, failFinish: false, errors: [] }
  page.on('pageerror', error => state.errors.push(error.message))
  await page.route('**/dev-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    let response = { code: 200, data: {} }
    if (path === '/getInfo') {
      response = { code: 200, user: { userId: 100, userName: 'test' }, roles: ['student'], permissions: [] }
    } else if (path === '/getRouters') {
      response.data = []
    } else if (path.endsWith('/session')) {
      response.data = { status: 'IN_PROGRESS' }
    } else if (path === '/exam/student/questions') {
      response.data = {
        examName: 'Autosave regression',
        endTime: new Date(Date.now() + (options.duration || 600000)).toISOString(),
        questions: [
          { questionId: 1, title: 'Choice', questionType: 'SINGLE_CHOICE', userAnswer: '',
            options: ['A', 'B', 'C'].map(label => ({ label, content: label })) },
          { questionId: 2, title: 'Text', questionType: 'SHORT_ANSWER', userAnswer: '' },
          { questionId: 3, title: 'Judge', questionType: 'JUDGE', userAnswer: '对',
            options: [{ label: 'A', content: '对' }, { label: 'B', content: '错' }] }
        ]
      }
    } else if (path === '/exam/student/submitAnswer') {
      const body = route.request().postDataJSON()
      state.saves.push(body)
      if (options.saveDelay) await new Promise(resolve => setTimeout(resolve, options.saveDelay))
      if (state.failSave) response = { code: 500, msg: 'Save unavailable' }
    } else if (path.endsWith('/finish')) {
      state.finishes++
      response = state.failFinish ? { code: 500, msg: 'Finish unavailable' } : { code: 200, data: { status: 'SUBMITTED' } }
    } else if (path === '/exam/student/waitInfo') {
      response.data = { status: 'none' }
    } else {
      throw new Error(`Unexpected API: ${path}`)
    }
    await route.fulfill({ json: response })
  })
  await page.goto(`${baseURL}/exam/answer?examId=1`)
  await page.getByText('Choice', { exact: true }).waitFor()
  return { page, state }
}

test('manual finish asks for confirmation; cancellation sends no finish request', async t => {
  const { page, state } = await fixture(t)
  await page.getByRole('button', { name: '交卷', exact: true }).click()
  await page.getByRole('dialog').waitFor()
  assert.equal(state.finishes, 0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(state.finishes, 0)
  assert.deepEqual(state.errors, [])
})

test('judgment restores legacy text and saves snapshot option labels', async t => {
  const { page, state } = await fixture(t)
  await page.locator('.q-btn').nth(2).click()
  assert.match(await page.locator('.option-item.selected').innerText(), /A/)
  await page.locator('.option-item').nth(1).click()
  await page.getByText('答案已保存', { exact: true }).waitFor()
  assert.equal(state.saves.at(-1).answer, 'B')
  assert.equal(state.saves.at(-1).questionId, 3)
  assert.deepEqual(state.errors, [])
})

test('rapid choices finish with the newest answer saved first', async t => {
  const { page, state } = await fixture(t, { saveDelay: 250 })
  await page.locator('.option-item').nth(0).click()
  await page.locator('.option-item').nth(1).click()
  await page.locator('.option-item').nth(2).click()
  await page.getByRole('button', { name: '交卷', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await page.waitForURL('**/exam/wait')
  assert.equal(state.saves.at(-1).answer, 'C')
  assert.equal(state.finishes, 1)
  assert.deepEqual(state.errors, [])
})

test('text autosaves without blur; failed saves block finish and allow retry', async t => {
  const { page, state } = await fixture(t)
  await page.locator('.q-btn').nth(1).click()
  state.failSave = true
  await page.locator('textarea').fill('draft text')
  await page.getByText('Save unavailable', { exact: true }).first().waitFor()
  assert.equal(state.saves.at(-1).answer, 'draft text')
  await page.getByRole('button', { name: '交卷', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await page.getByText('当前答案未保存，交卷已取消', { exact: true }).waitFor()
  assert.equal(state.finishes, 0)
  await page.getByRole('dialog').waitFor({ state: 'hidden' })
  state.failSave = false
  await page.getByRole('button', { name: '重试保存', exact: true }).click()
  await page.getByText('答案已保存', { exact: true }).waitFor()
  assert.equal(state.saves.at(-1).questionId, 2)
  assert.deepEqual(state.errors, [])
})

test('deadline locks editing and a failed automatic finish retries on reconnect', async t => {
  const { page, state } = await fixture(t, { duration: 2000 })
  state.failFinish = true
  await page.locator('.q-btn').nth(1).click()
  await page.getByText('交卷未确认，请重试交卷', { exact: true }).waitFor()
  assert.equal(await page.locator('textarea').isDisabled(), true)
  assert.equal(state.finishes, 1)
  state.failFinish = false
  await page.evaluate(() => window.dispatchEvent(new Event('online')))
  await page.waitForURL('**/exam/wait')
  assert.equal(state.finishes, 2)
  assert.deepEqual(state.errors, [])
})
