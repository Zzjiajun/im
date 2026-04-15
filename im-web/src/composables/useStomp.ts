import { Client, type IMessage } from '@stomp/stompjs'
import type { ChatMessageVO, SnowflakeId, WsEnvelope } from '@/types/api'

let client: Client | null = null

export type WsHandler = (env: WsEnvelope<unknown>) => void | Promise<void>

export type StompLifecycle = {
  /** 每次连接成功（含断线自动重连后） */
  onConnected?: () => void
  /** 断开或失败 */
  onDisconnected?: () => void
}

/** Spring @MessageMapping("/chat.xxx") → 发送目标 `/app/chat.xxx` */
export function stompPublish(destination: string, body: unknown) {
  if (!client?.connected) return false
  try {
    client.publish({
      destination,
      body: JSON.stringify(body),
    })
    return true
  } catch {
    return false
  }
}

export function stompTyping(conversationId: SnowflakeId, typing: boolean) {
  return stompPublish('/app/chat.typing', { conversationId, typing })
}

export function stompDeliver(messageIds: SnowflakeId[]) {
  if (!messageIds.length) return false
  return stompPublish('/app/chat.deliver', { messageIds })
}

export function isStompConnected(): boolean {
  return !!client?.connected
}

export function connectStomp(
  brokerURL: string,
  token: string,
  onEvent: WsHandler,
  lifecycle?: StompLifecycle
) {
  disconnectStomp()
  client = new Client({
    brokerURL,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      client?.subscribe('/user/queue/messages', (msg: IMessage) => {
        try {
          let env = JSON.parse(msg.body) as WsEnvelope<unknown>
          if (env?.data != null && typeof env.data === 'string') {
            try {
              env = { ...env, data: JSON.parse(env.data as string) }
            } catch {
              /* keep string */
            }
          }
          if (env?.event) {
            Promise.resolve(onEvent(env)).catch(() => {
              /* ignore async handler errors to avoid unhandled promise rejection */
            })
          }
        } catch {
          /* ignore malformed */
        }
      })
      lifecycle?.onConnected?.()
    },
    onWebSocketClose: () => {
      lifecycle?.onDisconnected?.()
    },
    onStompError: () => {
      lifecycle?.onDisconnected?.()
    },
  })
  client.activate()
}

export function disconnectStomp() {
  if (client) {
    client.deactivate()
    client = null
  }
}

export function isMessagePayload(data: unknown): data is ChatMessageVO {
  return (
    typeof data === 'object' &&
    data !== null &&
    'id' in data &&
    'conversationId' in data &&
    'senderId' in data
  )
}
