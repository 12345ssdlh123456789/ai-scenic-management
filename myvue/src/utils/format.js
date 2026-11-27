/**
 * 前端格式化工具
 * <p>
 * 这些函数存在的原因：模板里直接用 parseFloat(x).toFixed(2) 在 x 为
 * null/undefined 时会得到 "NaN"，页面显示 "¥NaN"；username.charAt(0)
 * 在 username 为 null 时会抛 TypeError 导致整块渲染失败。
 */

/** 金额格式化：任何非法值都显示 0.00，绝不出现 NaN */
export function formatMoney(value, fallback = '0.00') {
  const num = Number(value)
  if (!Number.isFinite(num)) return fallback
  return num.toFixed(2)
}

/** 金额（带 ¥ 前缀） */
export function formatYuan(value) {
  return '¥' + formatMoney(value)
}

/** 头像首字母：null/空串安全 */
export function firstLetter(name) {
  if (!name) return '?'
  const s = String(name).trim()
  return s ? s.charAt(0).toUpperCase() : '?'
}

/** 日期时间：去掉 T，截断到分钟 */
export function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').substring(0, 16)
}
