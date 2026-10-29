import { computed, reactive, ref } from 'vue'

export function useAnswerSaver(send) {
  const drafts = reactive(new Map())
  const saving = ref(false)
  const error = ref(false)
  const dirty = computed(() => [...drafts.values()].some(item => item.answer !== item.saved))
  let active = null

  function stage(question, answer) {
    if (!drafts.has(question.questionId)) {
      drafts.set(question.questionId, { question, answer, saved: question.userAnswer || '' })
    } else {
      drafts.get(question.questionId).answer = answer
    }
  }

  function flush() {
    if (active) return active
    saving.value = true
    // Defer the drain so active is assigned even when there are no dirty answers.
    active = Promise.resolve().then(async () => {
      try {
        for (;;) {
          const item = [...drafts.values()].find(entry => entry.answer !== entry.saved)
          if (!item) {
            error.value = false
            return true
          }
          const answer = item.answer
          await send(item.question.questionId, answer)
          item.saved = answer
          item.question.userAnswer = answer
        }
      } catch {
        error.value = true
        return false
      } finally {
        saving.value = false
        active = null
      }
    })
    return active
  }

  return { stage, flush, saving, error, dirty }
}
