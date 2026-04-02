/**
 * 解析 WebSocket 地址：优先 VITE_WS_URL；未配置时用当前页面的 host + `/ws-chat`
 *（开发时走 Vite 代理到后端，与 /api 同端口，避免只写错 WS 端口导致「发消息的人靠 HTTP 能更新、收消息的人收不到推送」）
 */
export function resolveWebSocketUrl(): string {
  const fromEnv = import.meta.env.VITE_WS_URL
  if (fromEnv != null && String(fromEnv).trim() !== '') {
    return String(fromEnv).trim()
  }
  if (typeof window === 'undefined') return ''
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws-chat`
}
