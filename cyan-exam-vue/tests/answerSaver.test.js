import test from 'node:test'
import assert from 'node:assert/strict'
import { useAnswerSaver } from '../src/views/exam/answer/useAnswerSaver.js'

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

test('serializes rapid edits and drains the latest answer before completing', async () => {
  const first = deferred()
  const calls = []
  const saver = useAnswerSaver(async (id, answer) => {
    calls.push([id, answer])
    if (calls.length === 1) await first.promise
  })
  const question = { questionId: 1, userAnswer: '' }
  saver.stage(question, 'A')
  const saving = saver.flush()
  await Promise.resolve()
  saver.stage(question, 'B')
  assert.equal(saver.flush(), saving)
  saver.stage(question, 'C')
  first.resolve()
  assert.equal(await saving, true)
  assert.deepEqual(calls, [[1, 'A'], [1, 'C']])
  assert.equal(question.userAnswer, 'C')
  assert.equal(saver.dirty.value, false)
})

test('reverting while a request is pending still saves the reverted answer', async () => {
  const first = deferred()
  const calls = []
  const saver = useAnswerSaver(async (id, answer) => {
    calls.push(answer)
    if (calls.length === 1) await first.promise
  })
  const question = { questionId: 1, userAnswer: '' }
  saver.stage(question, 'A')
  const saving = saver.flush()
  await Promise.resolve()
  saver.stage(question, '')
  first.resolve()
  await saving
  assert.deepEqual(calls, ['A', ''])
  assert.equal(question.userAnswer, '')
})

test('failed saves keep drafts dirty and can be retried', async () => {
  let fail = true
  const saver = useAnswerSaver(async () => {
    if (fail) throw new Error('offline')
  })
  const question = { questionId: 1, userAnswer: 'A' }
  saver.stage(question, 'B')
  assert.equal(await saver.flush(), false)
  assert.equal(question.userAnswer, 'A')
  assert.equal(saver.error.value, true)
  assert.equal(saver.dirty.value, true)
  fail = false
  assert.equal(await saver.flush(), true)
  assert.equal(question.userAnswer, 'B')
  assert.equal(saver.error.value, false)
  assert.equal(saver.saving.value, false)
})

test('answers remain bound to their question and unchanged drafts send nothing', async () => {
  const calls = []
  const saver = useAnswerSaver(async (id, answer) => calls.push([id, answer]))
  saver.stage({ questionId: 1, userAnswer: 'A' }, 'A')
  assert.equal(await saver.flush(), true)
  saver.stage({ questionId: 2, userAnswer: '' }, 'B')
  assert.equal(await saver.flush(), true)
  assert.deepEqual(calls, [[2, 'B']])
})
