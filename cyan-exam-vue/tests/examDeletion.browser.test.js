import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

for (const scenario of ['draft', 'published', 'mixed', 'protected', 'withdraw-failure', 'remove-only', 'ended-blank']) {
  test(`exam deletion: ${scenario}`, async t => {
    const browser = await chromium.launch({ headless: true, channel: 'msedge' })
    t.after(() => browser.close())
    const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
    await context.addCookies([{ name: 'Exam-Token-127-0-0-1-9010', value: 'delete-test', url: base }])
    const page = await context.newPage()
    const writes = [], errors = []
    page.on('pageerror', error => errors.push(error.message))
    let rows = [
      { examId: 1, examName: 'Expired draft', startTime: '2020-01-01 10:00:00', endTime: '2020-01-01 11:00:00', lifecycle: 'ACTIVE' },
      { examId: 2, examName: 'Published future exam', startTime: '2099-01-01 10:00:00', endTime: '2099-01-01 11:00:00', publishedAt: '2026-01-01', lifecycle: 'ACTIVE' },
      { examId: 3, examName: 'Archived exam', startTime: '2020-01-01 10:00:00', endTime: '2020-01-01 11:00:00', lifecycle: 'ARCHIVED' }
    ]
    if (scenario === 'ended-blank') {
      rows[0].publishedAt = '2020-01-01'
      rows[0].lifecycle = 'TERMINATED'
    }
    await page.route('**/dev-api/**', async route => {
      const req = route.request()
      const path = new URL(req.url()).pathname.replace('/dev-api', '')
      let response = { code: 200, data: [] }
      if (path === '/captchaImage') response = { code: 200, captchaEnabled: false }
      else if (path === '/getInfo') response = {
        code: 200, user: { userId: 9, userName: 'delete-test' }, roles: ['teacher'],
        permissions: scenario === 'remove-only' ? ['exam:exam:remove', 'exam:exam:list'] : ['*:*:*']
      }
      else if (path === '/getRouters') response.data = [{
        path: '/exam-management', component: 'Layout', children: [
          { path: '', name: 'DeletionTest', component: 'system/info/index', meta: { title: 'Exam management' } }
        ]
      }]
      else if (path === '/exam/exam/list') response = { code: 200, rows, total: rows.length }
      else if (path === '/exam/manage/2/withdraw') {
        writes.push(`${req.method()} ${path}`)
        assert.ok(req.postDataJSON().reason)
        if (scenario === 'withdraw-failure') response = { code: 500, msg: 'Withdrawal blocked by active session' }
        else rows[1].publishedAt = null
      }
      else if (req.method() === 'DELETE' && path.startsWith('/exam/exam/')) {
        writes.push(`${req.method()} ${path}`)
        if (scenario === 'protected') response = { code: 500, msg: 'Existing answers must be preserved' }
        else {
          const deletedIds = path.split('/').pop().split(',').map(Number)
          rows = rows.filter(row => !deletedIds.includes(row.examId))
        }
      }
      else if (!path.startsWith('/system/')) throw new Error(`Unexpected API: ${path}`)
      await route.fulfill({ json: response })
    })
    await page.goto(`${base}/exam-management`)
    await page.getByText('Expired draft', { exact: true }).waitFor()
    const expiredDraft = page.locator('.el-table__body tr').filter({ hasText: 'Expired draft' })
    if (scenario !== 'ended-blank') {
      await expiredDraft.getByText('草稿', { exact: true }).waitFor()
      if (scenario !== 'remove-only') {
        assert.equal(await expiredDraft.getByRole('button', { name: '修改' }).isEnabled(), true)
      }
    }
    await page.getByText('操作指引：准备考试、发布与删除', { exact: true }).click()
    await page.getByText('新增考试，设置名称和起止时间。', { exact: true }).waitFor()
    if (scenario === 'mixed') {
      await page.locator('.el-table__header-wrapper .el-checkbox').click()
      await page.getByRole('button', { name: '删除', exact: true }).first().click()
    } else {
      const name = ['published', 'withdraw-failure', 'remove-only'].includes(scenario) ? 'Published future exam' : 'Expired draft'
      await page.locator('.el-table__body tr').filter({ hasText: name }).getByRole('button', { name: '删除', exact: true }).click()
    }
    const dialog = page.getByRole('dialog', { name: '删除考试' })
    await dialog.waitFor()
    assert.equal(writes.length, 0)
    if (scenario === 'remove-only') {
      await dialog.getByText('已发布。请有考试编辑权限的管理员在开考前撤回，再删除。').waitFor()
      assert.equal(await dialog.getByRole('button', { name: /确认删除/ }).count(), 0)
    } else {
      if (scenario === 'draft') {
        await dialog.getByRole('button', { name: '取消', exact: true }).click()
        assert.equal(writes.length, 0)
        await page.locator('.el-table__body tr').filter({ hasText: 'Expired draft' }).getByRole('button', { name: '删除', exact: true }).click()
      }
      if (scenario === 'mixed') {
        await dialog.getByText('以下考试将保留，不会删除：').waitFor()
        await page.waitForTimeout(400)
        await page.screenshot({ path: join(tmpdir(), 'exam-deletion-desktop.png'), fullPage: true })
      }
      await dialog.getByRole('button', { name: /确认删除/ }).click()
      if (scenario === 'protected' || scenario === 'withdraw-failure') {
        const message = scenario === 'protected' ? 'Existing answers must be preserved' : 'Withdrawal blocked by active session'
        await dialog.getByRole('alert').filter({ hasText: message }).waitFor()
        assert.equal(await dialog.getByRole('button', { name: /确认删除/ }).isDisabled(), true)
      } else await dialog.waitFor({ state: 'hidden' })
      const expected = scenario === 'published' ? ['POST /exam/manage/2/withdraw', 'DELETE /exam/exam/2']
        : scenario === 'mixed' ? ['POST /exam/manage/2/withdraw', 'DELETE /exam/exam/1,2']
          : scenario === 'withdraw-failure' ? ['POST /exam/manage/2/withdraw'] : ['DELETE /exam/exam/1']
      assert.deepEqual(writes, expected)
    }
    assert.deepEqual(errors, [])
  })
}
