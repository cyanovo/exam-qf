import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const baseURL = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5173'

async function fixture(t, options = {}) {
  const browser = await chromium.launch({
    headless: true,
    channel: process.env.EXAM_TEST_BROWSER,
    executablePath: process.env.EXAM_TEST_BROWSER_PATH
  })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await context.addCookies([{ name: process.env.EXAM_TEST_TOKEN_KEY || 'Admin-Token', value: 'test-only', url: baseURL }])
  const page = await context.newPage()
  const state = { saves: [], submissions: [], finishes: 0, failSave: false, failFinish: false, failQuestions: options.failQuestions || false, errors: [] }
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
      if (state.failQuestions) response = { code: 500, msg: 'Questions unavailable' }
      else response.data = {
        examName: 'Autosave regression',
        endTime: new Date(Date.now() + (options.duration || 600000)).toISOString(),
        questions: options.python ? [
          { questionId: 4, title: 'Python task', questionType: 'PYTHON', userAnswer: '', maxScore: 10,
            description: 'Read a line from standard input.\nOutput the same line, followed by a newline.',
            initialCode: 'print(input())', snapshotHash: 'public-hash',
            samples: [{ input: 'hello\n', expected: 'hello\n' }] }
        ] : [
          { questionId: 1, title: 'Choice', questionType: 'SINGLE_CHOICE', userAnswer: '',
            options: ['A', 'B', 'C'].map(label => ({ label, content: label })) },
          { questionId: 2, title: 'Text', questionType: 'SHORT_ANSWER', userAnswer: '' },
          { questionId: 3, title: 'Judge', questionType: 'JUDGE', userAnswer: '对',
            options: [{ label: 'A', content: '对' }, { label: 'B', content: '错' }] }
        ],
        onsiteTasks: options.python ? [{ id: 8, title: 'Robot task', description: 'Raise the right arm, then stop.',
          maxScore: 15, state: 'OPEN', userAnswer: '' }] : []
      }
    } else if (path === '/exam/student/submitAnswer') {
      const body = route.request().postDataJSON()
      state.saves.push(body)
      if (options.saveDelay) await new Promise(resolve => setTimeout(resolve, options.saveDelay))
      if (state.failSave) response = { code: 500, msg: 'Save unavailable' }
    } else if (path === '/exam/code/1/history/4') {
      response.data = state.submissions.map(row => row.mode === 'SAMPLE'
        ? { ...row, feedback: [{ case: 1, passed: true, status: 3, stdout: 'hello\n', stderr: '' }] }
        : { ...row, state: 'SUCCEEDED', feedback: [{ input: 'SECRET_HIDDEN_INPUT' }], score: options.formalScore ?? 10 })
    } else if (path === '/exam/practical/1/8/history') {
      response.data = []
    } else if (path === '/exam/code/1') {
      const body = route.request().postDataJSON()
      state.submissions.unshift({ id: state.submissions.length + 1, mode: body.mode, state: 'QUEUED', receivedAt: '2026-10-29 12:00:00' })
      response.data = state.submissions[0]
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
  if (!options.failQuestions) await page.getByText(options.python ? 'Python task' : 'Choice', { exact: true }).waitFor()
  return { page, state }
}

test('Python starter stays a draft; sample output is visible but formal diagnostics stay private', async t => {
  const { page, state } = await fixture(t, { python: true })
  await page.locator('.monaco-editor').waitFor()
  assert.match(await page.locator('.python-description').innerText(), /Read a line.*\nOutput the same line/)
  assert.match(await page.locator('.monaco-editor').getAttribute('aria-label') || await page.locator('.view-lines').innerText(), /print|Python/)
  assert.match(await page.locator('.nav-title').innerText(), /0 \/ 2 已答/)
  await page.getByRole('button', { name: '保存草稿' }).click()
  await page.getByText('草稿已保存').waitFor()
  assert.equal(state.saves.at(-1).answer, 'print(input())')
  await page.getByRole('button', { name: '运行样例（不计分）' }).click()
  await page.getByText('公开样例运行', { exact: false }).waitFor()
  await page.getByText('hello', { exact: true }).last().waitFor({ timeout: 10000 })
  assert.equal(await page.getByText('10 / 10 分', { exact: true }).count(), 0)
  await page.getByRole('button', { name: '运行并评分（正式提交）' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click()
  await page.getByText('全部隐藏测试通过', { exact: true }).first().waitFor({ timeout: 10000 })
  await page.getByText('10 / 10 分', { exact: true }).waitFor()
  assert.equal(await page.getByText('10 / 10', { exact: true }).count(), 1)
  assert.equal(state.submissions.length, 2)
  assert.equal(state.submissions[0].mode, 'FORMAL')
  assert.doesNotMatch(await page.locator('.python-workbench').innerText(), /SECRET_HIDDEN_INPUT/)
  await page.locator('.q-btn').nth(1).click()
  await page.getByText('Robot task', { exact: true }).waitFor()
  await page.getByText('Raise the right arm, then stop.', { exact: true }).waitFor()
  await page.getByRole('button', { name: '提交代码', exact: true }).waitFor()
  assert.deepEqual(state.errors, [])
})

for (const width of [1440, 390]) {
  test(`partial formal score is not a pass and full statement fits at ${width}px`, async t => {
    const { page, state } = await fixture(t, { python: true, formalScore: 4 })
    await page.setViewportSize({ width, height: 960 })
    await page.locator('.python-description').waitFor()
    await page.getByRole('button', { name: '运行并评分（正式提交）' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '确定' }).click()
    await page.getByText('4 / 10 分', { exact: true }).waitFor({ timeout: 12000 })
    await page.getByText('未通过全部隐藏测试', { exact: true }).first().waitFor()
    assert.equal(await page.getByText('全部隐藏测试通过', { exact: true }).count(), 0)
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    await page.screenshot({ path: `${process.env.TEMP}/python-partial-${width}.png`, fullPage: true })
    assert.deepEqual(state.errors, [])
  })
}

test('manual finish asks for confirmation; cancellation sends no finish request', async t => {
  const { page, state } = await fixture(t)
  await page.getByRole('button', { name: '交卷', exact: true }).click()
  await page.getByRole('dialog').waitFor()
  assert.match(await page.getByRole('dialog').innerText(), /还有 2 题未作答/)
  assert.equal(state.finishes, 0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(state.finishes, 0)
  assert.deepEqual(state.errors, [])
})

test('answer card reflects unsaved edits and offers keyboard-operable options', async t => {
  const { page, state } = await fixture(t, { saveDelay: 600 })
  const choice = page.locator('.option-item').first()
  await choice.focus()
  await page.keyboard.press('Enter')
  await page.locator('.q-btn.pending').first().waitFor()
  assert.match(await page.locator('.nav-title').innerText(), /2 \/ 3 已答/)
  assert.equal(await choice.getAttribute('aria-pressed'), 'true')
  await page.getByText('答案已保存', { exact: true }).waitFor()
  assert.equal(state.saves.at(-1).answer, 'A')
  assert.deepEqual(state.errors, [])
})

test('question loading failure can be retried in place', async t => {
  const { page, state } = await fixture(t, { failQuestions: true })
  await page.getByText('试卷暂时无法加载').waitFor()
  state.failQuestions = false
  await page.getByRole('button', { name: '重新加载' }).click()
  await page.getByText('Choice', { exact: true }).waitFor()
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
