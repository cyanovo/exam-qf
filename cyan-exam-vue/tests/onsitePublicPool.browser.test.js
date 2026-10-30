import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

async function setup(t, role, width = 1440) {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.EXAM_TEST_EXECUTABLE ? { executablePath: process.env.EXAM_TEST_EXECUTABLE } : { channel: 'msedge' })
  })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width, height: 960 } })
  await context.addCookies([{ name: 'Exam-Token-127-0-0-1-8182', value: 'public-pool-test', url: base }])
  const page = await context.newPage()
  const calls = [], writes = [], errors = []
  const state = { score: null }
  page.on('pageerror', e => errors.push(e.message))
  t.after(() => assert.deepEqual(errors, []))
  await page.route('**/dev-api/**', async route => {
    const req = route.request()
    const path = new URL(req.url()).pathname.replace('/dev-api', '')
    calls.push(path)
    if (['POST', 'PUT', 'DELETE'].includes(req.method())) writes.push({ path, body: req.postDataJSON() })
    let response = { code: 200, data: [] }
    if (path === '/getInfo') response = {
      code: 200, user: { userId: role === 'student' ? 9 : 10, userName: role },
      roles: [role], permissions: role === 'student' ? [] : ['*:*:*']
    }
    else if (path === '/getRouters') response.data = role === 'student' ? [] : [{
      path: '/exam-management', component: 'Layout', children: [
        { path: '', name: 'PoolExamManagement', component: 'system/info/index', meta: { title: 'Exam management' } }
      ]
    }]
    else if (path === '/exam/student/waitInfo') response.data = { status: 'none' }
    else if (path === '/exam/exam/list') response = {
      code: 200, rows: [{ examId: 1, examName: 'Public pool exam', lifecycle: 'ACTIVE',
        startTime: '2099-01-01 10:00:00', endTime: '2099-01-01 11:00:00' }], total: 1
    }
    else if (path === '/exam/manage/1/questions') response.data = [
      { questionId: 20, title: 'Python sum', questionType: 'PYTHON', category: 'PYTHON', difficulty: 1 }
    ]
    else if (path === '/exam/manage/1/robot-questions') response.data = [
      { questionId: 21, title: 'Robot forward and stop', questionType: 'ROBOT_PRACTICAL', difficulty: 1 }
    ]
    else if (path === '/exam/onsite/exams') response.data = [{ examId: 1, examName: 'Public pool exam' }]
    else if (path === '/exam/onsite/1') response.data = {
      examName: 'Public pool exam', canManage: false, resultLocked: false,
      roster: [{ userId: 9, name: 'Submitted student' }, { userId: 11, name: 'Not submitted' }],
      tasks: [{ id: 3, title: 'Robot forward and stop', state: 'OPEN', durationMinutes: 10, maxScore: 10,
        canScore: true, submittedUsers: [9], items: [{ title: 'Stop correctly', maxScore: 10 }],
        scores: state.score ? [state.score] : [], events: [] }]
    }
    else if (path === '/exam/practical/1/3/history') response.data = [
      { id: 15, source: 'robot.stop()', receivedAt: '2026-10-30 01:00:00' }
    ]
    else if (path === '/exam/onsite/1/tasks/3/scores/9') {
      state.score = { ...req.postDataJSON(), userId: 9, judgeId: 10, judgeName: 'Current judge',
        total: 8, state: 'DRAFT', canEdit: true, canWithdraw: false, canApprove: false,
        revision: 1, points: '[8]' }
    }
    else if (path === '/exam/manage/1') response.data = {
      state: 'DRAFT', exam: { examName: 'Public pool exam', startTime: '2099-01-01 10:00:00',
        durationMinutes: 0, earlyEntryMinutes: 30 },
      policy: { entries: [], tieRule: 'SHARED', releasePolicy: 'DETAIL', retentionDays: 365 }, questions: [], tasks: [], history: []
    }
    else if (path === '/exam/onsite/1/tasks/from-bank') response.data = 3
    else if (path.startsWith('/system/config/')) response = { code: 200, msg: '' }
    await route.fulfill({ json: response })
  })
  return { page, calls, writes, state }
}

for (const width of [1440, 390]) {
  test(`student has no staff navigation at ${width}px`, { timeout: 60000 }, async t => {
    const { page, calls } = await setup(t, 'student', width)
    await page.goto(`${base}/index`)
    await page.getByText('暂无考试安排', { exact: true }).waitFor()
    assert.equal(await page.locator('.sidebar-container').count(), 0)
    assert.equal(await page.locator('.sidebar-toggle').count(), 0)
    assert.equal(await page.locator('.tags-view-container').count(), 0)
    assert.equal(await page.getByText('机器人实操评分', { exact: true }).count(), 0)
    await page.screenshot({ path: join(tmpdir(), `pool-student-${width}.png`), fullPage: true })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    await page.goto(`${base}/exam/onsite`)
    await page.waitForURL('**/index')
    assert.equal(calls.some(p => p.startsWith('/exam/onsite')), false)
  })

  test(`public pool and ownership at ${width}px`, { timeout: 60000 }, async t => {
    const { page, state, writes } = await setup(t, 'judge', width)
    await page.goto(`${base}/exam/onsite?examId=1`)
    await page.getByText('Submitted student', { exact: true }).waitFor()
    assert.equal(await page.getByText('Not submitted', { exact: true }).count(), 0)
    await page.getByRole('button', { name: '评分', exact: true }).click()
    await page.locator('.submitted-code').waitFor()
    await page.getByRole('spinbutton', { name: 'Stop correctly', exact: true }).fill('8')
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await page.getByText('草稿已保存', { exact: true }).waitFor()
    assert.equal(writes.at(-1).body.submissionId, 15)
    state.score.canEdit = false
    state.score.judgeId = 8
    state.score.judgeName = 'Other judge'
    await page.getByRole('button', { name: '刷新', exact: true }).click()
    await page.getByText('Other judge', { exact: true }).waitFor()
    await page.getByRole('button', { name: '查看', exact: true }).click()
    await page.getByText('其他评委已开始评分，当前只能查看。', { exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '提交评分', exact: true }).count(), 0)
    assert.equal(await page.getByRole('spinbutton', { name: 'Stop correctly', exact: true }).isDisabled(), true)
    await page.waitForTimeout(400)
    await page.screenshot({ path: join(tmpdir(), `pool-judge-${width}.png`), fullPage: true })
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  })
}

test('robot picker adds practical question without a designated judge', { timeout: 60000 }, async t => {
  const { page, writes } = await setup(t, 'teacher')
  await page.goto(`${base}/exam-management`)
  await page.locator('.el-table__body tr').filter({ hasText: 'Public pool exam' }).getByRole('button', { name: '选择题目', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '选择题目' })
  await dialog.waitFor()
  await dialog.locator('.el-form-item').filter({ hasText: '题目类型' }).locator('.el-select').click()
  await page.getByRole('option', { name: '机器人实操编程', exact: true }).click()
  await dialog.getByText('Robot forward and stop (机器人实操编程)', { exact: true }).waitFor()
  assert.equal(await dialog.getByText('绑定评委', { exact: true }).count(), 0)
  await dialog.locator('.el-transfer-panel').first().getByText('Robot forward and stop (机器人实操编程)', { exact: true }).click()
  await dialog.locator('.el-transfer__buttons button').nth(1).click()
  await dialog.getByRole('button', { name: '保存全部选题', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.deepEqual(writes, [
    { path: '/exam/exam/1/questions', body: [] },
    { path: '/exam/onsite/1/tasks/from-bank', body: { questionId: 21, durationMinutes: 10 } }
  ])
})

test('default picker includes robots and saves both kinds after switching filters', { timeout: 60000 }, async t => {
  const { page, writes } = await setup(t, 'teacher')
  await page.goto(`${base}/exam-management`)
  await page.locator('.el-table__body tr').first().getByRole('button', { name: '选择题目', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '选择题目' })
  const available = dialog.locator('.el-transfer-panel').first()
  await available.getByText('Robot forward and stop (机器人实操编程)', { exact: true }).click()
  await available.getByText('Python sum (Python编程)', { exact: true }).click()
  await dialog.locator('.el-transfer__buttons button').nth(1).click()
  await dialog.locator('.el-form-item').filter({ hasText: '题目类型' }).locator('.el-select').click()
  await page.getByRole('option', { name: 'Python编程', exact: true }).click()
  await dialog.getByRole('button', { name: '保存全部选题', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.deepEqual(writes, [
    { path: '/exam/exam/1/questions', body: [20] },
    { path: '/exam/exam/1/questionPoints', body: { 20: 10 } },
    { path: '/exam/onsite/1/tasks/from-bank', body: { questionId: 21, durationMinutes: 10 } }
  ])
})

test('arrangement creates practical question without a designated judge', { timeout: 60000 }, async t => {
  const { page, writes } = await setup(t, 'teacher')
  await page.goto(`${base}/exam/manage/1`)
  await page.getByRole('tab', { name: '章节与题目顺序' }).click()
  await page.getByRole('button', { name: '新增机器人实操编程题', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '新增机器人实操编程题' })
  await dialog.waitFor()
  assert.equal(await dialog.getByText('裁判', { exact: true }).count(), 0)
  await dialog.locator('.el-select').click()
  await page.getByRole('option', { name: 'Robot forward and stop', exact: true }).click()
  await dialog.getByRole('button', { name: '创建', exact: true }).click()
  await dialog.waitFor({ state: 'hidden' })
  assert.deepEqual(writes, [{ path: '/exam/onsite/1/tasks/from-bank', body: { questionId: 21, durationMinutes: 10 } }])
})
