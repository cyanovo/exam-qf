import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

for (const scenario of ['theory', 'practical-missing', 'practical-submitted']) {
  test(`ended exam grading: ${scenario}`, async t => {
    const browser = await chromium.launch({ headless: true, channel: 'msedge' })
    t.after(() => browser.close())
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
    await context.addCookies([{ name: 'Exam-Token-127-0-0-1-8182', value: 'grading-test', url: base }])
    const page = await context.newPage()
    const writes = [], errors = []
    page.on('pageerror', e => errors.push(e.message))
    await page.route('**/dev-api/**', async route => {
      const req = route.request()
      const path = new URL(req.url()).pathname.replace('/dev-api', '')
      let response = { code: 200, data: [] }
      if (path === '/getInfo') response = { code: 200, user: { userId: 1, userName: 'teacher' }, roles: ['admin'], permissions: ['*:*:*'] }
      else if (path === '/getRouters') response.data = [{
        path: '/exam-management', component: 'Layout',
        children: [{ path: '', name: 'EndedExamTest', component: 'system/info/index', meta: { title: 'Exams' } }]
      }]
      else if (path === '/exam/exam/list') response = { code: 200, total: 1, rows: [{
        examId: 1, examName: 'Ended exam', startTime: '2020-01-01 10:00:00', endTime: '2020-01-01 11:00:00',
        publishedAt: '2020-01-01', lifecycle: 'TERMINATED'
      }] }
      else if (path === '/exam/grading/students/1') response.data = [{
        userId: 2, studentName: 'Student', answered: 1, total: 1, graded: 0, pending: 1, score: 0,
        maxScore: 10, sessionStatus: 'AUTO_SUBMITTED'
      }]
      else if (path === '/exam/results/1') response.data = {
        examName: 'Ended exam', state: 'GRADING', reviewers: []
      }
      else if (path === '/exam/grading/detail/1/2') response.data = {
        examName: 'Ended exam', studentName: 'Student', sessionStatus: 'AUTO_SUBMITTED', resultsLocked: false,
        questions: [{ questionId: 3, recordId: 4, title: 'Question', type: 'FILL_BLANK', maxScore: 10, studentAnswer: 'answer', score: null }],
        revisions: []
      }
      else if (path === '/exam/onsite/exams') response.data = [{ examId: 1, examName: 'Ended exam' }]
      else if (path === '/exam/onsite/1') response.data = {
        examName: 'Ended exam', canManage: true, resultLocked: false, examEnded: true, roster: [{ userId: 2, name: 'Student' }],
        tasks: [{ id: 3, title: 'Robot task', maxScore: 10, state: 'OPEN', canScore: true, judgeId: 1,
          items: [{ title: 'Step', maxScore: 10 }], scores: [], events: [] }]
      }
      else if (path === '/exam/practical/1/3/history') response.data = scenario === 'practical-submitted'
        ? [{ id: 7, source: 'robot.stop()', receivedAt: '2020-01-01 10:30:00' }] : []
      else if (path === '/exam/grading/submitScore' || path === '/exam/onsite/1/tasks/3/scores/2') writes.push(req.postDataJSON())
      else if (!path.startsWith('/system/')) throw new Error(`Unexpected API ${path}`)
      await route.fulfill({ json: response })
    })
    await page.goto(`${base}/exam-management`)
    if (scenario === 'theory') {
      await page.getByRole('button', { name: '评分与成绩', exact: true }).click()
      await page.getByRole('tab', { name: '基础题与 Python', exact: true }).click()
      await page.getByRole('button', { name: '批改', exact: true }).click()
      await page.getByText('已到时交卷', { exact: true }).waitFor()
      await page.getByRole('spinbutton').fill('8')
      await page.getByRole('spinbutton').blur()
      await page.getByRole('button', { name: '保存批改' }).click()
      await page.getByText('批改已保存', { exact: true }).waitFor()
      assert.deepEqual(writes[0].scores, [{ recordId: 4, score: 8 }])
    } else {
      await page.getByRole('button', { name: '评分与成绩', exact: true }).click()
      await page.getByRole('tab', { name: '机器人实操', exact: true }).click()
      await page.getByRole('button', { name: '评分', exact: true }).click()
      if (scenario === 'practical-missing') {
        await page.getByText('该考生没有正式提交实操代码', { exact: false }).waitFor()
        assert.equal(await page.getByRole('button', { name: '提交评分', exact: true }).count(), 0)
        assert.deepEqual(writes, [])
        await page.getByRole('button', { name: '未提交，记0分', exact: true }).click()
        await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
        await page.getByText('已按白卷记0分', { exact: true }).waitFor()
        assert.deepEqual(writes[0].points, [0])
        assert.equal(writes[0].markBlank, true)
        assert.equal(writes[0].submissionId, null)
      } else {
        await page.getByRole('spinbutton', { name: 'Step', exact: true }).fill('8')
        await page.getByRole('button', { name: '提交评分', exact: true }).click()
        await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
        await page.getByText('评分已提交', { exact: true }).waitFor()
        assert.equal(writes[0].submissionId, 7)
        assert.deepEqual(writes[0].points, [8])
      }
    }
    assert.deepEqual(errors, [])
  })
}
