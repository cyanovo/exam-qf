import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

for (const width of [1440, 390]) {
  test(`practical code to step scoring at ${width}px`, async t => {
    const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER || 'msedge' })
    t.after(() => browser.close())
    const context = await browser.newContext({ viewport: { width, height: 960 } })
    await context.addCookies([{ name: process.env.EXAM_TEST_TOKEN_KEY || 'Exam-Token-127-0-0-1-8182', value: 'practical-test', url: base }])
    const page = await context.newPage()
    t.after(async () => { if (!page.isClosed()) await page.screenshot({ path: join(tmpdir(), `practical-last-${width}.png`), fullPage: true }).catch(() => {}) })
    let draft = 'robot.forward()', submissions = [], score = null, finished = false
    const calls = [], errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/dev-api/**', async route => {
      const req = route.request()
      const path = new URL(req.url()).pathname.replace('/dev-api', '')
      calls.push(path)
      let response = { code: 200, data: {} }
      if (path === '/getInfo') response = { code: 200, user: { userId: 9, userName: 'practical-test' }, roles: ['admin'], permissions: ['*:*:*'] }
      else if (path === '/getRouters') response.data = []
      else if (path.endsWith('/session')) response.data = { status: 'IN_PROGRESS' }
      else if (path === '/exam/student/questions') response.data = {
        examName: 'Robot programming', endTime: new Date(Date.now() + 600000).toISOString(),
        questions: [], onsiteTasks: [{ id: 3, title: 'Forward and stop', description: 'Drive forward, then stop.',
          maxScore: 15, userAnswer: draft, submittedSource: submissions[0]?.source }]
      }
      else if (path === '/exam/practical/1/3/draft') draft = req.postDataJSON().source
      else if (path === '/exam/practical/1/3/submit') {
        const body = req.postDataJSON()
        assert.equal(body.source, 'robot.stop()')
        response.data = { id: 15, source: body.source, state: 'SUBMITTED', mode: 'FORMAL', receivedAt: '2026-10-29 23:00:00' }
        submissions = [response.data]
      }
      else if (path === '/exam/practical/1/3/history') response.data = submissions
      else if (path === '/exam/student/1/finish') finished = true
      else if (path === '/exam/student/waitInfo') response.data = { status: 'none' }
      else if (path === '/exam/onsite/exams') response.data = [{ examId: 1, examName: 'Robot programming' }]
      else if (path === '/exam/onsite/1') response.data = {
        examName: 'Robot programming', canManage: true, resultLocked: false, roster: [{ userId: 9, name: 'Student' }],
        tasks: [{ id: 3, title: 'Forward and stop', state: 'OPEN', maxScore: 15, judgeName: 'Judge',
          durationMinutes: 10, canScore: true, items: [{ title: 'Forward', maxScore: 10 }, { title: 'Stop', maxScore: 5 }],
          scores: score ? [score] : [], events: [] }]
      }
      else if (path === '/exam/onsite/1/tasks/3/scores/9') {
        const body = req.postDataJSON()
        assert.equal(body.submissionId, 15)
        assert.deepEqual(body.points, [8, 5])
        score = { ...body, userId: 9, total: 13, state: 'SUBMITTED', revision: 1, points: JSON.stringify(body.points) }
      }
      else if (path.startsWith('/system/config/')) response = { code: 200, msg: '' }
      else throw new Error(`Unexpected API ${path}`)
      await route.fulfill({ json: response })
    })
    await page.goto(`${base}/exam/onsite?examId=1`)
    await page.getByRole('button', { name: '评分', exact: true }).click()
    await page.getByText('考生尚未提交代码', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '提交评分', exact: true }).count(), 0)
    await page.goto(`${base}/exam/answer?examId=1`)
    await page.locator('.monaco-editor').waitFor()
    assert.equal(await page.getByRole('button', { name: '成果与证据' }).count(), 0)
    assert.equal(await page.getByRole('button', { name: '运行样例' }).count(), 0)
    await page.locator('.monaco-editor .view-lines').click({ position: { x: 80, y: 10 } })
    await page.keyboard.press('Control+A')
    await page.keyboard.type('robot.stop()')
    for (let i = 0; i < 30 && draft !== 'robot.stop()'; i++) await page.waitForTimeout(100)
    assert.equal(draft, 'robot.stop()', `Calls: ${JSON.stringify(calls)}; editor: ${await page.locator('.monaco-editor').innerText()}`)
    await page.getByRole('button', { name: '交卷', exact: true }).click()
    await page.getByText('有实操代码尚未正式提交', { exact: false }).waitFor()
    assert.equal(finished, false)
    await page.reload()
    await page.locator('.monaco-editor').waitFor()
    assert.ok(await page.locator('.monaco-editor').innerText().then(s => s.includes('robot.stop()')))
    await page.getByRole('button', { name: '提交代码', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await page.getByText('已接收提交 15', { exact: true }).waitFor()
    assert.equal(calls.some(p => p.startsWith('/exam/code/')), false)
    await page.screenshot({ path: join(tmpdir(), `practical-student-${width}.png`), fullPage: true })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    await page.getByRole('button', { name: '交卷', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await page.waitForURL('**/exam/wait')
    assert.equal(finished, true)
    await page.goto(`${base}/exam/onsite?examId=1`)
    await page.getByRole('button', { name: '评分', exact: true }).click()
    await page.locator('.submitted-code').waitFor()
    assert.equal(await page.locator('.submitted-code').innerText(), 'robot.stop()')
    await page.getByRole('spinbutton', { name: 'Forward', exact: true }).fill('8')
    await page.getByRole('spinbutton', { name: 'Stop', exact: true }).fill('5')
    await page.screenshot({ path: join(tmpdir(), `practical-judge-${width}.png`), fullPage: true })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    await page.getByRole('button', { name: '提交评分', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await page.getByText('评分已提交', { exact: true }).waitFor()
    assert.equal(score.total, 13)
    await page.getByRole('button', { name: '查看', exact: true }).click()
    await page.locator('.submitted-code').waitFor()
    assert.equal(await page.getByRole('button', { name: '提交评分', exact: true }).count(), 0)
    assert.deepEqual(errors, [])
  })
}
