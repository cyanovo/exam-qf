import request from '@/utils/request'

// 查询学生列表
export function listStudent(query) {
  return request({
    url: '/system/student/list',
    method: 'get',
    params: query
  })
}

// 查询学生详细
export function getStudent(studentId) {
  return request({
    url: '/system/student/' + studentId,
    method: 'get'
  })
}

// 新增学生
export function addStudent(data) {
  return request({
    url: '/system/student',
    method: 'post',
    data: data
  })
}

// 修改学生
export function updateStudent(data) {
  return request({
    url: '/system/student',
    method: 'put',
    data: data
  })
}

// 删除学生
export function delStudent(studentIds) {
  return request({
    url: '/system/student/' + studentIds,
    method: 'delete'
  })
}

// 一键创建学生账号
export function importStudents(data) {
  return request({
    url: '/system/student/import',
    method: 'post',
    data: data
  })
}