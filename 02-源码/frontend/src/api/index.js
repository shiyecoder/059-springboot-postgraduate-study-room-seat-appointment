import http from './http'

/** 认证与个人中心 */
export const authApi = {
  login: (data) => http.post('/api/auth/login', data),
  me: () => http.get('/api/auth/me'),
  menus: () => http.get('/api/auth/menus'),
  profile: (data) => http.put('/api/auth/profile', data),
  password: (data) => http.post('/api/auth/password', data),
  logout: () => http.post('/api/auth/logout')
}

/** 自习室、座位、热力 */
export const roomApi = {
  page: (params) => http.get('/api/study-room/page', { params }),
  detail: (id) => http.get(`/api/study-room/${id}`),
  save: (data, id) => (id ? http.put(`/api/study-room/${id}`, data) : http.post('/api/study-room', data)),
  remove: (id) => http.delete(`/api/study-room/${id}`),
  seats: (params) => http.get('/api/seat/page', { params }),
  seatMap: (params) => http.get('/api/seat/map', { params }),
  disableSeat: (id, data) => http.post(`/api/seat/${id}/disable`, data),
  heatmap: (id, days) => http.get(`/api/study-room/${id}/heatmap`, { params: { days } })
}

/** 预约、签到、候补、门户 */
export const bookingApi = {
  page: (params) => http.get('/api/seat-booking/page', { params }),
  detail: (id) => http.get(`/api/seat-booking/${id}`),
  book: (seatId, data) => http.post(`/api/seat/${seatId}/book`, data),
  checkin: (id, data) => http.post(`/api/seat-booking/${id}/checkin`, data),
  checkout: (id) => http.post(`/api/seat-booking/${id}/checkout`),
  cancel: (id, data) => http.post(`/api/seat-booking/${id}/cancel`, data),
  leave: (id, data) => http.post(`/api/seat-booking/${id}/leave`, data || {}),
  back: (id) => http.post(`/api/seat-booking/${id}/back`),
  clear: (id, data) => http.post(`/api/seat-booking/${id}/clear`, data),
  waitlist: () => http.get('/api/booking/waitlist'),
  portalOverview: () => http.get('/api/portal/overview'),
  portalBookings: (params) => http.get('/api/portal/bookings', { params }),
  portalReport: (days) => http.get('/api/portal/report', { params: { days } })
}

/** 违约与黑名单 */
export const violationApi = {
  page: (params) => http.get('/api/violation-record/page', { params }),
  detail: (id) => http.get(`/api/violation-record/${id}`),
  appeal: (id, data) => http.post(`/api/violation-record/${id}/appeal`, data),
  handle: (id, data) => http.post(`/api/violation-record/${id}/handle`, data),
  blacklist: (params) => http.get('/api/blacklist-record/page', { params }),
  release: (data) => http.post('/api/blacklist/release', data)
}

/** 创新点：风险评分、规则版本、人工修正 */
export const riskApi = {
  student: (id) => http.get(`/api/student/${id}/booking-risk`),
  rule: () => http.get('/api/risk/rule'),
  saveRule: (data) => http.post('/api/risk/rule', data),
  snapshot: (studentId) => http.get('/api/risk/snapshot', { params: { studentId } }),
  correct: (data) => http.post('/api/risk/correct', data),
  effect: () => http.get('/api/risk/effect')
}

/** 统计看板与报表 */
export const statApi = {
  overview: () => http.get('/api/dashboard/overview'),
  charts: (days) => http.get('/api/dashboard/charts', { params: { days } }),
  utilization: (days) => http.get('/api/stat/utilization', { params: { days } }),
  college: (days) => http.get('/api/stat/college', { params: { days } }),
  violation: (days) => http.get('/api/stat/violation', { params: { days } })
}

/** 系统管理 */
export const systemApi = {
  dicts: () => http.get('/api/meta/dicts'),
  dictType: (type) => http.get(`/api/meta/dict/${type}`),
  dictTypes: () => http.get('/api/system/dict/types'),
  dictData: (type) => http.get('/api/system/dict/data', { params: { type } }),
  saveDictData: (data) => http.post('/api/system/dict/data', data),
  notices: () => http.get('/api/system/notice/list'),
  messages: (params) => http.get('/api/system/message/page', { params }),
  read: (id) => http.post(`/api/system/message/${id}/read`),
  readAll: () => http.post('/api/system/message/read-all'),
  users: (params) => http.get('/api/system/user/page', { params }),
  saveUser: (data, id) => (id ? http.put(`/api/system/user/${id}`, data) : http.post('/api/system/user', data)),
  resetPassword: (id) => http.post(`/api/system/user/${id}/reset-password`),
  roles: () => http.get('/api/system/role/list'),
  saveRole: (data, id) => (id ? http.put(`/api/system/role/${id}`, data) : http.post('/api/system/role', data)),
  roleMenus: (id) => http.get(`/api/system/role/${id}/menus`),
  saveRoleMenus: (id, menuIds) => http.post(`/api/system/role/${id}/menus`, { menuIds }),
  menuTree: () => http.get('/api/system/menu/tree'),
  logs: (params) => http.get('/api/system/log/page', { params })
}

export const fileApi = {
  upload: (file, bizType = 'common') => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('bizType', bizType)
    return http.post('/api/file/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  }
}
