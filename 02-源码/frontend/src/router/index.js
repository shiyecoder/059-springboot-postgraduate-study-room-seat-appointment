import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由与后端 sys_menu 的 path 一一对应（/dashboard/index、/room/list …），
 * 侧栏由后端下发的菜单树渲染，路由只负责把 path 映射到视图。
 */
const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: () => (localStorage.getItem('sb_user') && JSON.parse(localStorage.getItem('sb_user')).roleCode === 'STUDENT' ? '/portal/home' : '/dashboard/index'),
    children: [
      { path: 'dashboard/index', component: () => import('../views/Dashboard.vue'), meta: { title: '场馆看板', group: '场馆运营' } },
      { path: 'room/list', component: () => import('../views/RoomList.vue'), meta: { title: '自习室档案', group: '场馆资源' } },
      { path: 'room/detail/:id', component: () => import('../views/RoomDetail.vue'), meta: { title: '自习室详情', group: '场馆资源', active: '/room/list' } },
      { path: 'room/seat-map', component: () => import('../views/SeatMap.vue'), meta: { title: '座位平面图', group: '场馆资源' } },
      { path: 'booking/list', component: () => import('../views/BookingList.vue'), meta: { title: '预约管理', group: '预约与签到' } },
      { path: 'booking/detail/:id', component: () => import('../views/BookingDetail.vue'), meta: { title: '预约详情', group: '预约与签到', active: '/booking/list' } },
      { path: 'booking/checkin', component: () => import('../views/CheckinWorkbench.vue'), meta: { title: '签到核验台', group: '预约与签到' } },
      { path: 'booking/waitlist', component: () => import('../views/Waitlist.vue'), meta: { title: '候补队列', group: '预约与签到' } },
      { path: 'violation/list', component: () => import('../views/ViolationList.vue'), meta: { title: '违约记录', group: '违约管理' } },
      { path: 'violation/detail/:id', component: () => import('../views/ViolationDetail.vue'), meta: { title: '违约详情', group: '违约管理', active: '/violation/list' } },
      { path: 'violation/blacklist', component: () => import('../views/Blacklist.vue'), meta: { title: '黑名单管理', group: '违约管理' } },
      { path: 'insight/heatmap', component: () => import('../views/Heatmap.vue'), meta: { title: '需求热力', group: '数据洞察' } },
      { path: 'insight/report', component: () => import('../views/Report.vue'), meta: { title: '利用率报表', group: '数据洞察' } },
      { path: 'insight/rule', component: () => import('../views/RuleConfig.vue'), meta: { title: '规则配置', group: '数据洞察' } },
      { path: 'insight/college', component: () => import('../views/CollegeStat.vue'), meta: { title: '学院到馆', group: '数据洞察' } },
      { path: 'insight/risk/:id', component: () => import('../views/RiskDetail.vue'), meta: { title: '违约风险评分', group: '数据洞察', active: '/insight/rule' } },
      { path: 'system/user', component: () => import('../views/UserList.vue'), meta: { title: '用户管理', group: '系统管理' } },
      { path: 'system/role', component: () => import('../views/RoleList.vue'), meta: { title: '角色管理', group: '系统管理' } },
      { path: 'system/menu', component: () => import('../views/MenuList.vue'), meta: { title: '菜单管理', group: '系统管理' } },
      { path: 'system/dict', component: () => import('../views/DictList.vue'), meta: { title: '数据字典', group: '系统管理' } },
      { path: 'system/log', component: () => import('../views/OperLog.vue'), meta: { title: '操作日志', group: '系统管理' } },
      { path: 'me/notice', component: () => import('../views/NoticeList.vue'), meta: { title: '公告通知', group: '个人' } },
      { path: 'me/message', component: () => import('../views/MessageList.vue'), meta: { title: '站内消息', group: '个人' } },
      { path: 'me/profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心', group: '个人' } }
    ]
  },
  {
    path: '/portal',
    component: () => import('../layout/PortalLayout.vue'),
    redirect: '/portal/home',
    children: [
      { path: 'home', component: () => import('../views/PortalHome.vue'), meta: { title: '首页' } },
      { path: 'select', component: () => import('../views/SeatSelect.vue'), meta: { title: '选座预约' } },
      { path: 'bookings', component: () => import('../views/MyBookings.vue'), meta: { title: '我的预约' } },
      { path: 'checkin', component: () => import('../views/PortalCheckin.vue'), meta: { title: '签到' } },
      { path: 'report', component: () => import('../views/StudyReport.vue'), meta: { title: '学习报告' } },
      { path: 'violation', component: () => import('../views/MyViolations.vue'), meta: { title: '我的违约' } },
      { path: 'notice', component: () => import('../views/NoticeList.vue'), meta: { title: '公告通知', admin: true } },
      { path: 'message', component: () => import('../views/MessageList.vue'), meta: { title: '站内消息', admin: true } },
      { path: 'profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心', admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const token = localStorage.getItem('sb_token')
  if (to.meta.public) return true
  if (!token) return { path: '/login', query: { redirect: to.fullPath === '/' ? undefined : to.fullPath } }
  const user = JSON.parse(localStorage.getItem('sb_user') || 'null')
  // 学生只走门户；管理角色不进学生门户，避免越权页面报 4030
  if (to.path.startsWith('/portal') && !to.meta.admin && user?.roleCode !== 'STUDENT') {
    if (!['/portal/profile', '/portal/message', '/portal/notice'].includes(to.path)) return { path: '/dashboard/index' }
  }
  if (!to.path.startsWith('/portal') && to.path !== '/login' && user?.roleCode === 'STUDENT') {
    return { path: '/portal/home' }
  }
  document.title = `${to.meta.title || '静研座位'} · 考研自习室座位预约与学习打卡系统`
  return true
})

export default router
