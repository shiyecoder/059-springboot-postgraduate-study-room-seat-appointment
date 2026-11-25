import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * 统一请求封装：
 * 后端业务码 200 为成功，4010 为登录失效（清态回登录页），其余按异常提示。
 * 令牌存 localStorage 的 sb_token，与后端 JwtFilter 的 Authorization 头对齐。
 */
const http = axios.create({ baseURL: '', timeout: 20000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('sb_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

function goLogin() {
  localStorage.removeItem('sb_token')
  localStorage.removeItem('sb_user')
  localStorage.removeItem('sb_menus')
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code === 'number') {
      if (body.code === 0) return body.data
      if (body.code === 4010) {
        ElMessage.error(body.message || '登录已失效，请重新登录')
        goLogin()
        return Promise.reject(new Error(body.message))
      }
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(new Error(body.message || '操作失败'))
    }
    return body
  },
  (err) => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      ElMessage.error('没有访问权限或登录已失效')
      if (err.response?.status === 401) goLogin()
      return Promise.reject(err)
    }
    const msg = err.response?.data?.message || err.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http
