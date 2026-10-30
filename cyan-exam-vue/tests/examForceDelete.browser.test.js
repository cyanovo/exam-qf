import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5177'

for (const admin of [false, true]) {
  test(`force delete ${admin ? 'admin confirmation' : 'hidden from teacher'}`, async t => {
    const browser = await chromium.launch({ headless: true, channel: 'msedge' })
    t.after(() => browser.close())
    const context = await browser.newContext()
    await context.addCookies([{ name: 'Exam-Token-127-0-0-1-8181', value: 'force-test', url: base }])
    const page = await context.newPage()
    const requests = []
    let rows = [{ examId: 71, examName: 'Practice Exam', lifecycle: 'ARCHIVED',
      publishedAt: '2026-01-01', startTime: '2020-01-01 10:00:00', endTime: '2020-01-01 11:00:00' }]
    await page.route('**/dev-api/**', async route => {
      const request = route.request()
      const path = new URL(request.url()).pathname.replace('/dev-api', '')
      let response = { code: 200, data: [] }
      if (path === '/getInfo') response = {
        code: 200, user: { userId: admin ? 1 : 9, userName: 'tester' },
        roles: [admin ? 'admin' : 'teacher'], permissions: ['*:*:*']
      }
      else if (path === '/getRouters') response.data = [{
        path: '/exam-management', component: 'Layout',
        children: [{ path: '', name: 'ForceDeleteTest', component: 'system/info/index', meta: { title: 'Exams' } }]
      }]
      else if (path === '/exam/exam/list') response = { code: 200, rows, total: rows.length }
      else if (path === '/exam/manage/71/force' && request.method() === 'DELETE') {
        requests.push(request.postDataJSON())
        rows = []
      } else if (!path.startsWith('/system/')) throw new Error(`Unexpected API: ${path}`)
      await route.fulfill({ json: response })
    })
    await page.goto(`${base}/exam-management`)
    await page.getByText('Practice Exam', { exact: true }).waitFor()
    if (!admin) {
      assert.equal(await page.getByRole('button', { name: '强制删除' }).count(), 0)
      return
    }
    await page.getByRole('button', { name: '强制删除' }).click()
    const dialog = page.getByRole('dialog', { name: '强制删除考试' })
    await dialog.waitFor()
    const confirm = dialog.getByRole('button', { name: '永久删除' })
    assert.equal(await confirm.isDisabled(), true)
    await dialog.getByRole('textbox', { name: '输入考试全名确认' }).fill('Practice Exam')
    await dialog.getByRole('textbox', { name: '删除原因（至少5字）' }).fill('演示考试数据清理')
    assert.equal(await confirm.isEnabled(), true)
    assert.equal(requests.length, 0)
    await confirm.click()
    await dialog.waitFor({ state: 'hidden' })
    assert.deepEqual(requests, [{ examName: 'Practice Exam', reason: '演示考试数据清理' }])
  })
}
