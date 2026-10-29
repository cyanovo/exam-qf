import request from '@/utils/request'

// 查询题库列表
export function listQuestion(query) {
  return request({
    url: '/question/question/list',
    method: 'get',
    params: query
  })
}

// 查询题库详细
export function getQuestion(questionId) {
  return request({
    url: '/question/question/' + questionId,
    method: 'get'
  })
}

// 粘贴JSON导入
export function importJson(data) {
  return request({
    url: '/question/question/importJson',
    method: 'post',
    data: data
  })
}

// 新增题库
export function addQuestion(data) {
  return request({
    url: '/question/question',
    method: 'post',
    data: data
  })
}

// 修改题库
export function updateQuestion(data) {
  return request({
    url: '/question/question',
    method: 'put',
    data: data
  })
}

// 删除题库
export function delQuestion(questionId) {
  return request({
    url: '/question/question/' + questionId,
    method: 'delete'
  })
}
