import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

async function fixture(t, reviewer = false) {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
  await context.addCookies([{ name: 'Exam-Token-127-0-0-1-8182', value: 'workspace-test', url: base }])
  const page = await context.newPage()
  const state = { phase: reviewer ? 'REVIEW' : 'GRADING', theory: 6, robot: 8, robotSubmitted: true, locked: false, writes: [], reads: [], errors: [] }
  page.on('pageerror', e => state.errors.push(e.message))
  await page.route('**/dev-api/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname.replace('/dev-api', '')
    const writing = req.method() !== 'GET'
    if (writing) state.writes.push({ path, body: req.postDataJSON() })
    else state.reads.push(path)
    let response = { code: 200, data: [] }
    const scores = () => [
      { questionId: 3, title: '基础题', score: state.theory, maxScore: 10 },
      { taskId: 5, title: '机器人任务', score: state.robot, maxScore: 10 }
    ]
    if (path === '/getInfo') response = { code: 200, user: { userId: reviewer ? 9 : 1, userName: 'teacher' },
      roles: reviewer ? ['reviewer'] : ['admin'], permissions: reviewer ? ['exam:results:review'] : ['*:*:*'] }
    else if (path === '/getRouters') response.data = []
    else if (path === '/exam/results/1') response.data = {
      examName: '统一评分测试', state: state.phase, reviewers: [{ userId: 9, name: '复核员' }],
      ...(state.phase !== 'GRADING' ? { version: 1, snapshot: {
        rows: [{ userId: 2, studentName: '学生甲', total: state.theory + state.robot, rank: 1, scores: scores() }],
        taskStatistics: [], statistics: { registered: 1, completed: 1, highest: state.theory + state.robot, lowest: state.theory + state.robot, average: state.theory + state.robot }
      } } : {})
    }
    else if (path === '/exam/grading/students/1') response.data = [{
      userId: 2, studentName: '学生甲', score: state.theory, maxScore: 10, answered: 1, total: 1,
      graded: 1, pending: 0, gradingComplete: true, sessionStatus: 'GRADED'
    }]
    else if (path === '/exam/grading/detail/1/2') response.data = {
      examName: '统一评分测试', studentName: '学生甲', sessionStatus: 'GRADED', resultsLocked: state.phase !== 'GRADING',
      questions: [{ questionId: 3, recordId: 4, title: '基础题', type: 'SHORT_ANSWER', maxScore: 10, studentAnswer: '回答', score: state.theory }]
    }
    else if (path === '/exam/grading/submitScore') state.theory = req.postDataJSON().scores[0].score
    else if (path === '/exam/onsite/1') response.data = {
      examName: '统一评分测试', canManage: true, resultLocked: state.phase !== 'GRADING', examEnded: true,
      roster: [{ userId: 2, name: '学生甲' }],
      tasks: [{ id: 5, title: '机器人任务', state: state.locked ? 'LOCKED' : 'OPEN', canScore: true, maxScore: 10,
        durationMinutes: 10, submittedUsers: [2], items: [{ title: '动作完成', maxScore: 10 }], events: [],
        scores: state.robotSubmitted ? [{ userId: 2, total: state.robot, state: 'SUBMITTED', points: `[${state.robot}]`, canEdit: false }] : []
      }]
    }
    else if (path === '/exam/practical/1/5/history') response.data = [{ id: 7, source: 'robot.stop()', receivedAt: '2026-10-30' }]
    else if (path === '/exam/onsite/1/tasks/5/scores/2') { state.robot = req.postDataJSON().points[0]; state.robotSubmitted = true }
    else if (path === '/exam/onsite/1/tasks/5/lock') state.locked = true
    else if (path === '/exam/results/1/request') state.phase = 'REVIEW'
    else if (path === '/exam/results/1/review/approve') state.phase = 'APPROVED'
    else if (path === '/exam/results/1/review/release') state.phase = 'RELEASED'
    else if (!path.startsWith('/system/')) throw new Error(`Unexpected API: ${path}`)
    await route.fulfill({ json: response })
  })
  await page.goto(`${base}/exam/grading/workspace/1`)
  await page.getByRole('heading', { name: '统一评分测试', exact: true }).waitFor()
  return { page, state }
}

async function confirmReason(page, name) {
  await page.getByRole('button', { name, exact: true }).click()
  const box = page.locator('.el-message-box')
  await box.locator('textarea').fill('核对完成')
  await box.getByRole('button', { name: '确定', exact: true }).click()
}

test('one page: live total, in-page grading, practical lock, review and release', async t => {
  const { page, state } = await fixture(t)
  await page.getByText('14 / 20', { exact: true }).waitFor()
  assert.equal(state.writes.length, 0)
  await page.getByRole('tab', { name: '基础题与 Python', exact: true }).click()
  await page.getByRole('button', { name: '批改', exact: true }).click()
  const drawer = page.locator('.el-drawer')
  await drawer.getByRole('spinbutton').fill('7')
  await drawer.getByRole('spinbutton').blur()
  await drawer.locator('.el-drawer__close-btn').click()
  await page.locator('.el-message-box').getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(await drawer.getByRole('spinbutton').inputValue(), '7')
  await drawer.locator('textarea').fill('核对答案调整分数')
  await drawer.getByRole('button', { name: '保存批改', exact: true }).click()
  await page.getByText('批改已保存', { exact: true }).waitFor()
  await drawer.locator('.el-drawer__close-btn').click()
  await page.getByRole('tab', { name: '机器人实操', exact: true }).click()
  await page.getByRole('button', { name: '查看', exact: true }).click()
  await page.getByText('robot.stop()', { exact: true }).waitFor()
  await page.locator('.onsite-dialog').getByRole('button', { name: '关闭', exact: true }).click()
  await confirmReason(page, '锁定评分')
  await page.getByText('已锁定', { exact: true }).waitFor()
  await page.getByRole('tab', { name: '成绩与发布', exact: true }).click()
  await page.getByText('15 / 20', { exact: true }).waitFor()
  await page.waitForTimeout(600)
  await page.screenshot({ path: 'tests/grading-workspace-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(400)
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
  await page.screenshot({ path: 'tests/grading-workspace-mobile.png', fullPage: true })
  await confirmReason(page, '提交复核')
  await page.getByText('复核中 · 已锁定', { exact: true }).waitFor()
  await confirmReason(page, '通过复核')
  await page.getByText('复核通过 · 待发布', { exact: true }).waitFor()
  await confirmReason(page, '发布成绩')
  await page.getByText('已发布', { exact: true }).waitFor()
  assert.ok(page.url().includes('/exam/grading/workspace/1'))
  assert.equal(state.writes.filter(w => w.path.includes('/request')).length, 1)
  assert.deepEqual(state.errors, [])
})

test('pending robot score is not treated as a completed result; scoring stays in page', async t => {
  const { page, state } = await fixture(t)
  state.robotSubmitted = false
  await page.getByRole('button', { name: '刷新当前视图', exact: true }).click()
  await page.getByText('待评分 1 题', { exact: false }).waitFor()
  await page.getByText('评分未完成', { exact: true }).waitFor()
  await page.getByRole('tab', { name: '机器人实操', exact: true }).click()
  assert.equal(await page.getByRole('button', { name: '锁定评分', exact: true }).isDisabled(), true)
  await page.getByRole('button', { name: '评分', exact: true }).click()
  await page.getByRole('spinbutton', { name: '动作完成', exact: true }).fill('9')
  await page.getByRole('button', { name: '提交评分', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
  await page.getByText('评分已提交', { exact: true }).waitFor()
  await page.getByRole('tab', { name: '成绩与发布', exact: true }).click()
  await page.getByText('15 / 20', { exact: true }).waitFor()
  assert.equal(state.robot, 9)
  assert.deepEqual(state.errors, [])
})

test('reviewer sees only authorized result view, without grading API requests', async t => {
  const { page, state } = await fixture(t, true)
  await page.getByText('复核中 · 已锁定', { exact: true }).waitFor()
  assert.equal(await page.getByRole('tab').count(), 1)
  assert.equal(await page.getByRole('button', { name: '提交复核', exact: true }).count(), 0)
  assert.equal(await page.getByRole('button', { name: '通过复核', exact: true }).count(), 1)
  assert.equal(state.reads.some(p => p.startsWith('/exam/onsite') || p.startsWith('/exam/grading/students')), false)
  assert.deepEqual(state.errors, [])
})
