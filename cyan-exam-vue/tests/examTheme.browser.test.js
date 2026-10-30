import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5178'

for (const preference of [null, 'auto']) {
  test(`system dark mode does not override default light (${preference})`, async t => {
    const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER || 'msedge' })
    t.after(() => browser.close())
    const context = await browser.newContext({ colorScheme: 'dark' })
    await context.addInitScript(value => {
      if (value === null) localStorage.removeItem('vueuse-color-scheme')
      else localStorage.setItem('vueuse-color-scheme', value)
    }, preference)
    const page = await context.newPage()
    await page.route('**/dev-api/captchaImage', route => route.fulfill({ json: { code: 200, captchaEnabled: false } }))
    await page.goto(`${base}/login`)
    await page.locator('input').first().waitFor()
    const isDark = () => page.locator('html').evaluate(el => el.classList.contains('dark'))
    assert.equal(await isDark(), false)
    await page.emulateMedia({ colorScheme: 'light' })
    await page.emulateMedia({ colorScheme: 'dark' })
    assert.equal(await isDark(), false)
    assert.equal(await page.evaluate(() => localStorage.getItem('vueuse-color-scheme')), 'light')
  })
}

async function contrast(locator) {
  return locator.evaluate(el => {
    const rgb = value => value.match(/[\d.]+/g).slice(0, 3).map(Number)
    const luminance = color => rgb(color).map(v => {
      v /= 255
      return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4
    }).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0)
    let parent = el
    while (parent && ['rgba(0, 0, 0, 0)', 'transparent'].includes(getComputedStyle(parent).backgroundColor)) {
      parent = parent.parentElement
    }
    const fg = luminance(getComputedStyle(el).color)
    const bg = luminance(parent ? getComputedStyle(parent).backgroundColor : 'rgb(255, 255, 255)')
    return (Math.max(fg, bg) + 0.05) / (Math.min(fg, bg) + 0.05)
  })
}

for (const theme of ['light', 'dark']) {
  for (const width of [1440, 390]) {
    test(`${theme} exam and dossier remain readable at ${width}px`, async t => {
      const browser = await chromium.launch({ headless: true, channel: process.env.EXAM_TEST_BROWSER || 'msedge' })
      t.after(() => browser.close())
      const context = await browser.newContext({ viewport: { width, height: 960 }, colorScheme: theme })
      await context.addCookies([{ name: process.env.EXAM_TEST_TOKEN_KEY || 'Exam-Token-127-0-0-1-8182', value: 'theme-test', url: base }])
      await context.addInitScript(theme => localStorage.setItem('vueuse-color-scheme', theme), theme)
      const page = await context.newPage()
      const errors = []
      page.on('pageerror', error => errors.push(error.message))
      await page.route('**/dev-api/**', async route => {
        const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
        let response = { code: 200, data: {} }
        if (path === '/getInfo') response = { code: 200, user: { userId: 100, userName: 'theme-test' }, roles: ['student'], permissions: [] }
        else if (path === '/getRouters') response.data = []
        else if (path.endsWith('/session')) response.data = { status: 'IN_PROGRESS' }
        else if (path === '/exam/student/questions') response.data = {
          examName: 'Display regression', endTime: new Date(Date.now() + 600000).toISOString(),
          questions: [
            { questionId: 1, questionType: 'SINGLE_CHOICE', title: 'Python function keyword?',
              section: 'Section 1', sectionDescription: 'Chapter description', hint: '1', userAnswer: '',
              options: [{ label: 'A', content: 'func' }, { label: 'B', content: 'def' }] },
            { questionId: 2, questionType: 'SHORT_ANSWER', title: 'Explain your answer', userAnswer: 'Saved response' }
          ]
        }
        else if (path === '/exam/student/submitAnswer') response.data = {}
        else if (path === '/exam/dossier/1') response.data = {
          examName: 'Display regression', archived: false, canProcess: false,
          roster: [{ userId: 100, name: 'Student' }], tasks: [], incidents: [], files: []
        }
        else throw new Error(`Unexpected API: ${path}`)
        await route.fulfill({ json: response })
      })
      await page.goto(`${base}/exam/answer?examId=1`)
      await page.locator('.question-title').waitFor()
      assert.equal(await page.locator('html').evaluate(el => el.classList.contains('dark')), theme === 'dark')
      for (const selector of ['.question-card > h3', '.chapter-text', '.question-hint .el-alert__title', '.option-content']) {
        assert.ok(await contrast(page.locator(selector).first()) >= 4.5, `Low contrast: ${selector}`)
      }
      assert.equal(await page.getByText('仅本机预览，不录制、不上传，不使用麦克风。', { exact: true }).count(), 0)
      const hint = await page.locator('.question-hint').boundingBox()
      const options = await page.locator('.options-list').boundingBox()
      assert.ok(options.y >= hint.y + hint.height + 12)
      await page.locator('.option-item').first().click()
      await page.getByText('答案已保存', { exact: true }).waitFor()
      await page.waitForTimeout(250)
      assert.ok(await contrast(page.locator('.option-item.selected .option-content')) >= 4.5)
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      await page.screenshot({ path: join(tmpdir(), `exam-theme-${theme}-${width}.png`), fullPage: true })
      await page.locator('.q-btn').nth(1).click()
      assert.ok(await contrast(page.locator('textarea')) >= 4.5)
      await page.getByRole('button', { name: '异常与材料', exact: true }).click()
      await page.locator('.dossier h2').filter({ hasText: 'Display regression' }).waitFor()
      for (const selector of ['.dossier h2', '.el-table th.el-table__cell', '.el-table__empty-text']) {
        assert.ok(await contrast(page.locator(selector).first()) >= 4.5, `Low contrast: ${selector}`)
      }
      await page.screenshot({ path: join(tmpdir(), `dossier-theme-${theme}-${width}.png`), fullPage: true })
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
      assert.deepEqual(errors, [])
    })
  }
}
