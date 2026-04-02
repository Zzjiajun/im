import type { SnowflakeId } from '@/types/api'

/**
 * 规范为十进制数字字符串数组，供 API JSON 使用。
 * 禁止对雪花 ID 使用 Number()：超过 Number.MAX_SAFE_INTEGER 会精度丢失，导致后端校验好友等失败。
 */
export function normalizeSnowflakeIds(ids: readonly unknown[]): SnowflakeId[] {
  const out: SnowflakeId[] = []
  for (const x of ids) {
    const s = String(x).trim()
    if (/^\d+$/.test(s)) out.push(s)
  }
  return out
}

/** 同长度数字字符串可按字典序比较大小，适用于雪花 ID 排序 */
export function sortSnowflakeIds(ids: SnowflakeId[]): SnowflakeId[] {
  return [...ids].sort((a, b) => {
    if (a.length !== b.length) return a.length - b.length
    return a < b ? -1 : a > b ? 1 : 0
  })
}
