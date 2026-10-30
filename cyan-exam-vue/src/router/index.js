import { createWebHistory, createRouter } from 'vue-router'
/* Layout */
import Layout from '@/layout'
// 公共路由
export const constantRoutes = [
  {
    path: '/exam/manage',
    component: Layout,
    hidden: true,
    children: [{path: ':examId', component: () => import('@/views/exam/manage/index.vue'), meta: {title:'考试编排与场次'}}]
  },
  {path:'/exam/dossier/:examId', component:()=>import('@/views/exam/dossier/index.vue'), hidden:true, meta:{title:'异常与材料'}},
  {
    path: '/change-password',
    component: () => import('@/views/change-password.vue'),
    hidden: true,
    meta: { title: '首次登录修改密码' }
  },
  {
    path: '/exam/onsite',
    component: Layout,
    children: [{
      path: '',
      name: 'ExamOnsite',
      component: () => import('@/views/exam/onsite/index.vue'),
      meta: { title: '机器人实操评分', icon: 'edit' }
    }]
  },
  {
    path: '/exam/results',
    component: () => import('@/views/exam/results/index.vue'),
    hidden: true,
    meta: { title: '我的成绩' }
  },
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true
  },
  // 学生端：考试等待页（无需菜单，hidden: true）
  {
    path: '/exam/wait',
    component: () => import('@/views/exam/wait/index.vue'),
    name: 'ExamWait',
    hidden: true,
    meta: { title: '考试等待' }
  },
  // 学生端：答题页（无需菜单，hidden: true）
  {
    path: '/exam/answer',
    component: () => import('@/views/exam/answer/index.vue'),
    name: 'ExamAnswer',
    hidden: true,
    meta: { title: '答题页' }
  },
  // 教师端：批改管理（使用 Layout 包裹，子路由批量隐藏）
  {
    path: '/exam/grading',
    component: Layout,
    hidden: true,   // 如果希望侧边栏显示批改管理，就去掉此句，并在若依菜单管理中配置
    redirect: 'noredirect',
    meta: { title: '批改管理', icon: 'edit' },
    children: [
      {
        path: 'workspace/:examId',
        component: () => import('@/views/exam/grading/workspace.vue'),
        name: 'ExamGradingWorkspace',
        meta: { title: '评分与成绩' },
        hidden: true
      },
      {
        path: 'results/:examId',
        component: () => import('@/views/exam/grading/workspace.vue'),
        name: 'ExamResultsReview',
        meta: { title: '评分与成绩' },
        hidden: true
      },
      {
        path: '',
        component: () => import('@/views/exam/grading/index.vue'),
        name: 'ExamGrading',
        meta: { title: '批改管理' }
      },
      {
        path: 'students/:examId',
        component: () => import('@/views/exam/grading/workspace.vue'),
        name: 'ExamGradingStudents',
        meta: { title: '评分与成绩' },
        hidden: true
      },
      {
        path: 'detail/:examId/:userId',
        component: () => import('@/views/exam/grading/detail.vue'),
        name: 'ExamGradingDetail',
        meta: { title: '批改详情' },
        hidden: true
      }
    ]
  },
  {
    path: '/register',
    component: () => import('@/views/register'),
    hidden: true
  },
  {
    path: "/:pathMatch(.*)*",
    component: () => import('@/views/error/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401'),
    hidden: true
  },
  {
    path: '',
    component: Layout,
    redirect: '/index',
    children: [
      {
        path: '/index',
        component: () => import('@/views/index'),
        name: 'Index',
        meta: { title: '首页', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/lock',
    component: () => import('@/views/lock'),
    hidden: true,
    meta: { title: '锁定屏幕' }
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile/:activeTab?',
        component: () => import('@/views/system/user/profile/index'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  }
]

// 动态路由（原有，保持不变）
export const dynamicRoutes = [
  {
    path: '/system/user-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:user:edit'],
    children: [
      {
        path: 'role/:userId(\\d+)',
        component: () => import('@/views/system/user/authRole'),
        name: 'AuthRole',
        meta: { title: '分配角色', activeMenu: '/system/user' }
      }
    ]
  },
  {
    path: '/system/role-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:role:edit'],
    children: [
      {
        path: 'user/:roleId(\\d+)',
        component: () => import('@/views/system/role/authUser'),
        name: 'AuthUser',
        meta: { title: '分配用户', activeMenu: '/system/role' }
      }
    ]
  },
  {
    path: '/system/dict-data',
    component: Layout,
    hidden: true,
    permissions: ['system:dict:list'],
    children: [
      {
        path: 'index/:dictId(\\d+)',
        component: () => import('@/views/system/dict/data'),
        name: 'Data',
        meta: { title: '字典数据', activeMenu: '/system/dict' }
      }
    ]
  },
  {
    path: '/monitor/job-log',
    component: Layout,
    hidden: true,
    permissions: ['monitor:job:list'],
    children: [
      {
        path: 'index/:jobId(\\d+)',
        component: () => import('@/views/monitor/job/log'),
        name: 'JobLog',
        meta: { title: '调度日志', activeMenu: '/monitor/job' }
      }
    ]
  },
  {
    path: '/tool/gen-edit',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: 'index/:tableId(\\d+)',
        component: () => import('@/views/tool/gen/editTable'),
        name: 'GenEdit',
        meta: { title: '修改生成配置', activeMenu: '/tool/gen' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

export default router
