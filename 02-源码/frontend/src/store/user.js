import { reactive, computed } from 'vue'

/**
 * 轻量会话状态：令牌、用户、后端下发的菜单树（决定侧栏渲染，避免前端硬编码权限）。
 * localStorage 键统一 sb_ 前缀，只在这里读写。
 */
const state = reactive({
  token: localStorage.getItem('sb_token') || '',
  user: JSON.parse(localStorage.getItem('sb_user') || 'null'),
  menus: JSON.parse(localStorage.getItem('sb_menus') || '[]')
})

/** 角色码 → 中文名（与后端 role_code 一致，不带 ROLE_ 前缀） */
export const ROLE_LABEL = {
  ADMIN: '系统管理员',
  OPERATION: '场馆运营主管',
  ROOM_MANAGER: '自习室管理员',
  COUNSELOR: '学院学工老师',
  STUDENT: '考研学生'
}

export function useUserStore() {
  const isLogin = computed(() => !!state.token)
  const roleCode = computed(() => state.user?.roleCode || '')
  const isStudent = computed(() => state.user?.roleCode === 'STUDENT')
  const isAdmin = computed(() => state.user?.roleCode === 'ADMIN')
  const canManage = computed(() => ['ADMIN', 'OPERATION'].includes(state.user?.roleCode))
  const hasRole = (...codes) => codes.includes(state.user?.roleCode)

  function restore() {
    state.token = localStorage.getItem('sb_token') || ''
    state.user = JSON.parse(localStorage.getItem('sb_user') || 'null')
    state.menus = JSON.parse(localStorage.getItem('sb_menus') || '[]')
  }

  function setLogin(data) {
    state.token = data.token
    state.user = { ...data }
    state.menus = data.menus || []
    localStorage.setItem('sb_token', data.token)
    localStorage.setItem('sb_user', JSON.stringify(state.user))
    localStorage.setItem('sb_menus', JSON.stringify(state.menus))
  }

  function setUser(user) {
    state.user = { ...state.user, ...user }
    localStorage.setItem('sb_user', JSON.stringify(state.user))
  }

  function setMenus(menus) {
    state.menus = menus || []
    localStorage.setItem('sb_menus', JSON.stringify(state.menus))
  }

  function logout() {
    state.token = ''
    state.user = null
    state.menus = []
    localStorage.removeItem('sb_token')
    localStorage.removeItem('sb_user')
    localStorage.removeItem('sb_menus')
  }

  return { state, isLogin, roleCode, isStudent, isAdmin, canManage, hasRole, restore, setLogin, setUser, setMenus, logout }
}
