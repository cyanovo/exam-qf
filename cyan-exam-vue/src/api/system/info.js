import request from '@/utils/request'

// 查询考试信息列表
export function listCyanExam(query) {
  return request({
    url: '/exam/exam/list',
    method: 'get',
    params: query
  })
}

// 查询考试信息详细
export function getCyanExam(examId) {
  return request({
    url: '/exam/exam/' + examId,
    method: 'get'
  })
}

// 新增考试信息
export function addCyanExam(data) {
  return request({
    url: '/exam/exam',
    method: 'post',
    data: data
  })
}

// 修改考试信息
export function updateCyanExam(data) {
  return request({
    url: '/exam/exam',
    method: 'put',
    data: data
  })
}

export function publishCyanExam(examId) {
  return request({
    url: '/exam/exam/' + examId + '/publish',
    method: 'post'
  })
}

// 删除考试信息
export function delCyanExam(examIds) {
  return request({
    url: '/exam/exam/' + examIds,
    method: 'delete'
  })
}

// 获取考试已分配的学生ID列表
export function getStudentIds(examId) {
  return request({
    url: '/exam/exam/' + examId + '/studentIds',
    method: 'get'
  })
}

// 保存学生分配
export function saveStudents(examId, userIds) {
  return request({
    url: '/exam/exam/' + examId + '/students',
    method: 'put',
    data: userIds
  })
}

// 获取考试已选择的题目ID列表
export function getQuestionIds(examId) {
  return request({
    url: '/exam/exam/' + examId + '/questionIds',
    method: 'get'
  })
}

// 保存题目选择
export function saveQuestions(examId, questionIds) {
  return request({
    url: '/exam/exam/' + examId + '/questions',
    method: 'put',
    data: questionIds
  })
}

export function getQuestionPoints(examId) {
  return request({
    url: '/exam/exam/' + examId + '/questionPoints',
    method: 'get'
  })
}

export function saveQuestionPoints(examId, points) {
  return request({
    url: '/exam/exam/' + examId + '/questionPoints',
    method: 'put',
    data: points
  })
}
