import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

for (const width of [1440, 390]) {
  test(`plain login remains light and usable at ${width}px`, async t => {
    const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER || 'msedge' })
    t.after(() => browser.close())
    const page = await browser.newPage({ viewport: { width, height: 900 }, colorScheme: 'dark' })
    let refreshes = 0
    await page.route('**/dev-api/captchaImage', route => {
      refreshes++
      return route.fulfill({ json: {
        code: 200, captchaEnabled: true, uuid: 'login-test',
        img: 'R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'
      } })
    })
    await page.goto(`${base}/login`)
    await page.getByRole('button', { name: '刷新验证码' }).waitFor()
    assert.equal(await page.locator('.login-form').evaluate(el => getComputedStyle(el).backgroundColor), 'rgb(255, 255, 255)')
    assert.equal(await page.locator('canvas, .glow-orb').count(), 0)
    await page.getByRole('button', { name: '刷新验证码' }).click()
    await page.waitForTimeout(250)
    assert.ok(refreshes >= 2)
    await page.getByRole('textbox', { name: '账号', exact: true }).fill('')
    await page.getByLabel('密码', { exact: true }).fill('')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.getByText('请输入您的账号', { exact: true }).waitFor()
    await page.getByText('请输入您的密码', { exact: true }).waitFor()
    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    await page.screenshot({ path: join(tmpdir(), `login-plain-${width}.png`), fullPage: true })
  })
}
