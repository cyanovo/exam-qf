import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const base = process.env.EXAM_TEST_URL || 'http://127.0.0.1:5177'

async function fixture(t, mode = 'live', duration = 600000) {
  const browser = await chromium.launch({
    headless: true, channel: process.env.EXAM_TEST_BROWSER || 'msedge',
    args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--disable-gpu']
  })
  t.after(() => browser.close())
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await context.addCookies([{ name: process.env.EXAM_TEST_TOKEN_KEY || 'Exam-Token-127-0-0-1-8181', value: 'camera-test', url: base }])
  await context.addInitScript(({ mode }) => {
    const original = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
    window.cameraStreams = []
    window.cameraRequests = []
    window.cameraMode = mode
    navigator.mediaDevices.getUserMedia = async constraints => {
      window.cameraRequests.push(constraints)
      if (window.cameraMode === 'denied') throw new DOMException('Denied', 'NotAllowedError')
      if (window.cameraMode === 'missing') throw new DOMException('Missing', 'NotFoundError')
      const stream = await original(constraints)
      window.cameraStreams.push(stream)
      if (window.cameraMode === 'late') await new Promise(resolve => { window.resolveCamera = resolve })
      return stream
    }
  }, { mode })
  const page = await context.newPage()
  page.on('console', msg => { if (msg.text().includes('[ExamCamera]')) console.log(msg.text()) })
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  await page.route('**/dev-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/dev-api', '')
    let response = { code: 200, data: {} }
    if (path === '/getInfo') response = { code: 200, user: { userId: 100, userName: 'camera-test' }, roles: ['student'], permissions: [] }
    else if (path === '/getRouters') response.data = []
    else if (path.endsWith('/session')) response.data = { status: 'IN_PROGRESS' }
    else if (path === '/exam/student/questions') response.data = {
      examName: '摄像头功能测试', endTime: new Date(Date.now() + duration).toISOString(),
      questions: [{ questionId: 1, questionType: 'SHORT_ANSWER', title: '请简述 Python 列表的用途', userAnswer: '' }]
    }
    else if (path === '/exam/student/waitInfo') response.data = { status: 'none' }
    else if (!path.endsWith('/finish') && path !== '/exam/student/submitAnswer') throw new Error(`Unexpected API ${path}`)
    await route.fulfill({ json: response })
  })
  await page.goto(`${base}/exam/answer?examId=1`)
  await page.locator('textarea').waitFor()
  if (mode === 'live') {
    await page.waitForFunction(() => document.querySelector('.camera-status')?.textContent === '已开启', { }, { timeout: 10000 })
      .catch(async error => {
        console.log(await page.locator('.exam-camera').allTextContents(), errors)
        throw error
      })
  }
  return { page, errors }
}

async function finish(page) {
  await page.getByRole('button', { name: '交卷', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await page.waitForURL('**/exam/wait')
}

test('camera displays real fake-device frames, fits mobile, survives cancelled finish and stops on submit', async t => {
  const { page, errors } = await fixture(t)
  await page.getByText('已开启', { exact: true }).waitFor()
  await page.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
  assert.equal(await page.evaluate(() => window.cameraRequests[0].audio), false)
  assert.equal(await page.evaluate(() => window.cameraStreams[0].getAudioTracks().length), 0)
  assert.equal(await page.locator('.privacy-note').count(), 0)
  await page.screenshot({ path: 'tests/camera-desktop.png', fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: 'tests/camera-mobile.png', fullPage: true })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
  await page.getByRole('button', { name: '交卷', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  assert.equal(await page.evaluate(() => window.cameraStreams[0].getVideoTracks()[0].readyState), 'live')
  await finish(page)
  assert.equal(await page.evaluate(() => window.cameraStreams[0].getVideoTracks()[0].readyState), 'ended')
  assert.deepEqual(errors, [])
})

test('permission denial does not block answers; retry and disconnect recovery work', async t => {
  const { page, errors } = await fixture(t, 'denied')
  await page.getByText(/摄像头权限未获允许/).waitFor()
  await page.locator('textarea').fill('camera denial must not lose answers')
  await page.getByText('答案已保存', { exact: true }).waitFor()
  await page.evaluate(() => { window.cameraMode = 'live' })
  await page.getByRole('button', { name: '重新开启' }).click()
  await page.getByText('已开启', { exact: true }).waitFor()
  await page.evaluate(() => window.cameraStreams[0].getVideoTracks()[0].dispatchEvent(new Event('ended')))
  await page.getByText(/摄像头已断开/).waitFor()
  assert.equal(await page.evaluate(() => window.cameraStreams[0].getVideoTracks()[0].readyState), 'ended')
  await page.getByRole('button', { name: '重新开启' }).click()
  await page.getByText('已开启', { exact: true }).waitFor()
  await finish(page)
  assert.deepEqual(errors, [])
})

test('late permission result after leaving exam is immediately released', async t => {
  const { page, errors } = await fixture(t, 'late')
  await page.waitForFunction(() => typeof window.resolveCamera === 'function')
  await finish(page)
  await page.evaluate(() => window.resolveCamera())
  await page.waitForFunction(() => window.cameraStreams[0].getVideoTracks()[0].readyState === 'ended')
  assert.deepEqual(errors, [])
})

test('missing camera has actionable error without repeated requests', async t => {
  const { page } = await fixture(t, 'missing')
  await page.getByText(/未检测到摄像头/).waitFor()
  assert.equal(await page.evaluate(() => window.cameraRequests.length), 1)
  assert.equal(await page.locator('textarea').isEnabled(), true)
})

test('exam expiry releases camera', async t => {
  const { page, errors } = await fixture(t, 'live', 4500)
  await page.getByText('已开启', { exact: true }).waitFor()
  await page.waitForURL('**/exam/wait')
  assert.equal(await page.evaluate(() => window.cameraStreams[0].getVideoTracks()[0].readyState), 'ended')
  assert.deepEqual(errors, [])
})
