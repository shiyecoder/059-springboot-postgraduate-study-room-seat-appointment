/** 展示层格式化工具：时间去掉秒、比例转百分比、文本截断、空值占位 */

export function hhmm(v) {
  if (!v) return '--'
  const s = String(v)
  return s.length >= 5 ? s.slice(0, 5) : s
}

export function dt(v) {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 16)
}

export function d(v) {
  if (!v) return '--'
  return String(v).slice(0, 10)
}

export function pct(v, digits = 1) {
  if (v === null || v === undefined || v === '') return '--'
  const n = Number(v)
  return `${(n > 1 ? n : n * 100).toFixed(digits)}%`
}

export function num(v, digits = 0) {
  if (v === null || v === undefined || v === '') return '--'
  return Number(v).toFixed(digits)
}

/** 本地日期（中国时区），避免 toISOString 的 UTC 偏移导致默认日期差一天 */
export function localDate(offsetDays = 0) {
  const d0 = new Date(Date.now() + offsetDays * 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d0.getFullYear()}-${p(d0.getMonth() + 1)}-${p(d0.getDate())}`
}

export function orDash(v) {
  return v === null || v === undefined || v === '' ? '--' : v
}

/** 状态 → 标签色（与设计系统 .tag 的 b/g/a/r/o 对齐） */
export const STATUS_TONE = {
  待生效: 'o',
  待签到: 'a',
  学习中: 'b',
  已完成: 'g',
  已取消: '',
  已违约: 'r',
  待处理: 'a',
  申诉中: 'o',
  已撤销: '',
  已确认: 'r',
  生效中: 'r',
  已解除: 'g',
  已过期: '',
  开放: 'g',
  满员: 'a',
  维护中: 'r',
  已关闭: '',
  低风险: 'g',
  中风险: 'a',
  高风险: 'r'
}

export function tone(status) {
  return STATUS_TONE[status] ?? ''
}

/** 预约状态 → Element Plus tag 类型 */
export function epType(status) {
  return { 待生效: 'info', 待签到: 'warning', 学习中: 'primary', 已完成: 'success', 已取消: 'info', 已违约: 'danger' }[status] || 'info'
}
