<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useVoiceCallStore } from '@/stores/voiceCall'
import { useRealtimeStore } from '@/stores/realtime'
import { useNotificationStore } from '@/stores/notification'
import {
  connectStomp,
  disconnectStomp,
  isMessagePayload,
} from '@/composables/useStomp'
import { resolveWebSocketUrl } from '@/utils/wsUrl'
import type { RecallWsPayload, SnowflakeId, WsEnvelope } from '@/types/api'
import type { NotificationVO } from '@/types/api'

const auth = useAuthStore()
const chat = useChatStore()
const voiceCall = useVoiceCallStore()
const realtime = useRealtimeStore()
const notificationStore = useNotificationStore()
const wsUrl = resolveWebSocketUrl()

// 增强的WebSocket连接状态监控
const connectionAttempts = ref(0)
const maxConnectionAttempts = 5
const wsDebugEnabled = ref(false)

function wsDebugLog(next?: boolean) {
  if (typeof next === 'boolean') wsDebugEnabled.value = next
  return wsDebugEnabled.value
}

function idEq(a: SnowflakeId | null | undefined, b: SnowflakeId | null | undefined) {
  if (a == null || b == null) return false
  return String(a) === String(b)
}

function logWs(level: 'info' | 'warn' | 'error', message: string) {
  if (wsDebugEnabled.value) {
    console.log(`[WS ${level.toUpperCase()}]`, new Date().toLocaleTimeString(), message)
  }
}

async function handleWs(env: WsEnvelope<unknown>) {
  logWs('info', `收到事件: ${env.event}`)

  if (await voiceCall.handleWsEvent(env)) {
    return
  }

  // 处理消息事件
  if (env.event === 'MESSAGE' && isMessagePayload(env.data)) {
    logWs('info', `收到消息事件: 会话${env.data.conversationId}`)
    void chat.applyWsPayload(env.data)
    return
  }

  // 处理其他事件
  if (env.event === 'RECALL' && env.data && typeof env.data === 'object') {
    chat.applyWsRecall(env.data as RecallWsPayload)
    return
  }

  if (env.event === 'EDIT' && isMessagePayload(env.data)) {
    chat.applyWsEdit(env.data)
    return
  }

  if (env.event === 'REACTION' && isMessagePayload(env.data)) {
    chat.applyWsReaction(env.data)
    return
  }

  if (env.event === 'DELIVERED' && env.data && typeof env.data === 'object') {
    const d = env.data as { conversationId?: SnowflakeId; messageIds?: SnowflakeId[] }
    chat.applyWsDelivered(d)
    return
  }

  if (env.event === 'READ' && env.data && typeof env.data === 'object') {
    const d = env.data as { conversationId?: SnowflakeId; messageIds?: SnowflakeId[] }
    chat.applyWsRead(d)
    return
  }

  // 处理正在输入事件
  if (env.event === 'TYPING' && env.data && typeof env.data === 'object') {
    const typingData = env.data as { conversationId: SnowflakeId; userId: SnowflakeId; typing: boolean }
    logWs('info', `收到输入指示: 会话${typingData.conversationId}, 用户${typingData.userId}`)
    window.dispatchEvent(new CustomEvent('im-typing', { detail: typingData }))
    return
  }

  if (env.event === 'MESSAGE_PINNED' && isMessagePayload(env.data)) {
    const vo = env.data
    if (idEq(vo.conversationId, chat.activeId)) {
      window.dispatchEvent(new CustomEvent('im-pinned', { detail: vo }))
    }
  }

  // 处理通知事件
  if (env.event === 'NOTIFICATION' && env.data && typeof env.data === 'object') {
    const notification = env.data as NotificationVO
    logWs('info', `收到通知: ${notification.title}`)

    // 更新通知store
    notificationStore.addNotification(notification)

    // 触发通知事件
    window.dispatchEvent(new CustomEvent('im-notification', { detail: notification }))
    return
  }
}

function bindWs(token: string | null) {
  disconnectStomp()
  if (!token || !wsUrl) {
    realtime.setStatus('none')
    return
  }

  connectionAttempts.value++
  logWs('info', `尝试连接WebSocket (${connectionAttempts.value}/${maxConnectionAttempts}): ${wsUrl}`)

  realtime.setStatus('offline')

  if (connectionAttempts.value > maxConnectionAttempts) {
    logWs('error', '超过最大连接尝试次数，停止重连')
    return
  }

  connectStomp(wsUrl, token, handleWs, {
    onConnected: () => {
      connectionAttempts.value = 0
      logWs('info', '✅ WebSocket连接成功')
      realtime.setStatus('live')
      const id = chat.activeId
      if (id) void chat.syncNewerMessages(id)
    },
    onDisconnected: () => {
      logWs('warn', '❌ WebSocket连接断开，5秒后重连...')
      realtime.setStatus('offline')
      // 5秒后尝试重连
      setTimeout(() => {
        if (auth.isLoggedIn) {
          bindWs(auth.token)
        }
      }, 5000)
    }
  })
}

// 监听认证状态变化
watch(() => auth.token, (newToken, oldToken) => {
  logWs('info', `认证状态变化: ${oldToken ? '已登录' : '未登录'} -> ${newToken ? '已登录' : '未登录'}`)

  if (newToken && newToken !== oldToken) {
    logWs('info', '用户登录，连接WebSocket')
    bindWs(newToken)
  } else if (!newToken && oldToken) {
    logWs('info', '用户登出，断开WebSocket')
    disconnectStomp()
    realtime.setStatus('none')
  }
}, { immediate: true })

// 监听通知事件
onMounted(() => {
  logWs('info', 'GlobalRealtimeBridge 组件已挂载')

  // 初始化未读计数
  if (auth.isLoggedIn) {
    notificationStore.loadUnreadCount()
  }

  window.addEventListener('im-notification', ((e: Event) => {
    const customEvent = e as CustomEvent<NotificationVO>
    logWs('info', `收到通知事件: ${customEvent.detail.title}`)
  }) as EventListener)
})

onUnmounted(() => {
  logWs('info', 'GlobalRealtimeBridge 组件卸载，断开WebSocket')
  disconnectStomp()
})

// 暴露调试开关给开发者
if (import.meta.env.DEV) {
  ;(window as any).wsDebugLog = wsDebugLog;
  (window as any).wsRealtimeStatus = () => realtime.status;
  (window as any).wsConnect = () => bindWs(auth.token);
  (window as any).wsDisconnect = () => disconnectStomp();
}
</script>

<template>
  <!-- 这个组件不渲染任何内容 -->
</template>