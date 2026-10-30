import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash, randomUUID } from 'node:crypto'

const base = process.env.EXAM_PYTHON_API_URL || 'http://127.0.0.1:8182'
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
async function api(token, path, method = 'GET', body) {
  const response = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const result = await response.json()
  assert.equal(result.code, 200, `${path}: ${JSON.stringify(result)}`)
  return result
}
async function login(username) {
  return (await api(null, '/login', 'POST', { username, password: 'Demo2026!Onsite' })).token
}
function localDate(date) {
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

// Opt-in: creates a separate exam assigned only to the existing demo student.
test('real publication includes robot task, full Python statement and input-based hidden grading',
  { skip: process.env.EXAM_PROGRAMMING_REAL_TEST !== '1', timeout: 360000 }, async () => {
    const owner = await login('demo_owner'), student = await login('demo_student')
    const name = `Programming regression ${Date.now()}`
    // The exam API persists minute precision.
    const start = new Date(Math.ceil((Date.now() + 60000) / 60000) * 60000)
    await api(owner, '/exam/exam', 'POST', {
      examName: name, startTime: localDate(start), endTime: localDate(new Date(start.getTime() + 3600000)),
      status: '0', remark: 'Isolated programming regression; demo student only'
    })
    const exams = await api(owner, `/exam/exam/list?examName=${encodeURIComponent(name)}`)
    const examId = exams.rows.find(row => row.examName === name).examId
    console.log(`Dedicated regression exam: ${examId}`)
    await api(owner, `/exam/exam/${examId}/students`, 'PUT', [91004])
    await api(owner, `/exam/exam/${examId}/questions`, 'PUT', [70, 73])
    await api(owner, `/exam/exam/${examId}/questionPoints`, 'PUT', { 70: 10, 73: 10 })
    const taskId = (await api(owner, `/exam/onsite/${examId}/tasks/from-bank`, 'POST', {
      questionId: 94, durationMinutes: 10
    })).data
    await api(owner, `/exam/exam/${examId}/publish`, 'POST')
    await sleep(Math.max(0, start.getTime() - Date.now() + 1000))
    const paper = (await api(student, `/exam/student/questions?examId=${examId}`)).data
    assert.equal(paper.onsiteTasks.length, 1)
    assert.equal(paper.onsiteTasks[0].id, taskId)
    assert.equal(paper.onsiteTasks[0].state, 'OPEN')
    assert.match(paper.onsiteTasks[0].description, /60/)
    assert.equal(paper.onsiteTasks[0].rubric, undefined)
    const sum = paper.questions.find(q => q.questionId === 70)
    const run = paper.questions.find(q => q.questionId === 73)
    for (const q of [sum, run]) {
      assert.ok(q.description.length > 60)
      assert.equal(q.maxScore, 10)
      assert.equal(q.answer, undefined)
      assert.equal(q.samples.length, 1)
    }
    async function submit(q, mode, source) {
      const submission = (await api(student, `/exam/code/${examId}`, 'POST', {
        questionId: q.questionId, mode, source, snapshotHash: q.snapshotHash,
        sourceHash: createHash('sha256').update(source).digest('hex'), key: randomUUID()
      })).data
      for (let attempt = 0; attempt < 50; attempt++) {
        const result = (await api(student, `/exam/code/submission/${submission.id}`)).data
        if (result.state === 'FAILED') assert.fail('Sandbox infrastructure failed')
        if (result.state === 'SUCCEEDED') {
          if (mode === 'FORMAL') assert.equal(result.feedback, undefined)
          console.log(`Question ${q.questionId}: ${mode}, score=${result.score ?? 'not scored'}`)
          await sleep(10000)
          return result
        }
        await sleep(1500)
      }
      assert.fail('Judge did not complete')
    }
    const sample = await submit(sum, 'SAMPLE', 'print(8)')
    assert.equal(sample.feedback[0].passed, true)
    assert.equal(sample.score, undefined)
    assert.equal((await submit(sum, 'FORMAL', 'print(8)')).score, 0)
    assert.equal((await submit(sum, 'FORMAL', 'a,b=map(int,input().split())\nprint(a+b)')).score, 10)
    assert.equal((await submit(run, 'FORMAL', 'print(4)')).score, 4)
    assert.equal((await submit(run, 'FORMAL',
      'n=int(input())\na=list(map(int,input().split()))\nbest=cur=1\n'
      + 'for i in range(1,n):\n cur=cur+1 if a[i]>a[i-1] else 1\n best=max(best,cur)\nprint(best)')).score, 10)
  })
