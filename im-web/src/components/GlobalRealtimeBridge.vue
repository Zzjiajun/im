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
import { useDesktopNotification } from '@/composables/useDesktopNotification'
import { useNotificationSound } from '@/composables/useNotificationSound'
import { resolveWebSocketUrl } from '@/utils/wsUrl'
import type { RecallWsPayload, SnowflakeId, WsEnvelope } from '@/types/api'
import type { NotificationVO } from '@/types/api'

const auth = useAuthStore()
const chat = useChatStore()
const voiceCall = useVoiceCallStore()
const realtime = useRealtimeStore()
const notificationStore = useNotificationStore()
const wsUrl = resolveWebSocketUrl()

// 桌面通知 & 声音
const desktopNotify = useDesktopNotification()
const notificationSound = useNotificationSound()

// 增强的WebSocket连接状态监控
const connectionAttempts = ref(0)
const maxConnectionAttempts = 5
const wsDebugEnabled = ref(false)

/** 会话是否被当前用户设为免打扰 */
function isConversationMuted(conversationId: SnowflakeId): boolean {
  const conv = chat.conversations.find(c => String(c.conversationId) === String(conversationId))
  return conv?.muted === true
}

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
    const vo = env.data

    // 免打扰会话不做通知弹窗
    const muted = isConversationMuted(vo.conversationId)

    if (!muted) {
      const isActiveConv = idEq(vo.conversationId, chat.activeId)
      const isSelf = idEq(vo.senderId, auth.user?.id)

      if (!isActiveConv && !isSelf) {
        // 非活跃会话且有其他人发消息 → 弹窗
        messageToastRef.value?.pushMessage(vo)
      }

      if (!isSelf) {
        // 非自己发的消息 → 桌面通知 + 声音
        desktopNotify.notifyNewMessage(
          vo.senderNickname || '用户',
          previewForNotify(vo),
          vo.senderAvatar,
          async () => {
            // 点击桌面通知跳转到会话
            const { useRouter } = await import('vue-router')
            const router = useRouter()
            router.push(`/?openConv=${vo.conversationId}`)
          },
        )
        notificationSound.playMessageSound()
      }
    }

    void chat.applyWsPayload(vo)
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

  // 处理通知事件（好友申请、群邀请等）
  if (env.event === 'NOTIFICATION' && env.data && typeof env.data === 'object') {
    const notification = env.data as NotificationVO
    logWs('info', `收到通知: ${notification.title}`)

    // 更新通知store
    notificationStore.addNotification(notification)

    // 触发通知事件（用于NotificationBell刷新）
    window.dispatchEvent(new CustomEvent('im-notification', { detail: notification }))

    // 好友申请 → 弹窗快捷操作
    if (notification.type === 'FRIEND_REQUEST' && notification.data) {
      try {
        const data = typeof notification.data === 'string'
          ? JSON.parse(notification.data)
          : notification.data
        if (data?.friendRequestId && notification.senderId) {
          friendRequestToastRef.value?.pushFriendRequest(
            data.friendRequestId,
            notification.senderId,
            notification.senderNickname || '未知用户',
            notification.senderAvatar,
            // 验证信息从通知内容中提取
            notification.content ? notification.content.split(': ').pop() : null,
          )
        }
      } catch {
        // 解析失败不影响
      }
    }

    // 通知 → 桌面通知（免打扰类型的通知不弹）
    desktopNotify.notifyAppNotification(
      notification.title || '新通知',
      notification.content || '',
      notification.senderAvatar,
    )

    // @提及 → 播放声音
    if (notification.type === 'MENTION') {
      notificationSound.playMessageSound()
    }

    return
  }
}

function previewForNotify(vo: { type: string; content?: string | null; mediaUrl?: string | null }): string {
  if (vo.type === 'IMAGE') return '[图片]'
  if (vo.type === 'VIDEO') return '[视频]'
  if (vo.type === 'VOICE') return '[语音]'
  if (vo.type === 'FILE') return '[文件]'
  if (vo.type === 'SYSTEM') return '[系统消息]'
  if (vo.content) return vo.content.slice(0, 80)
  return '[消息]'
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
      logWs('warn', '❌ WebSocket连接断开')
      realtime.setStatus('offline')
      // 不再手动重连：STOMP Client 已配置 reconnectDelay: 5000 自动重连
      // 手动重连会与自动重连冲突，产生重复连接
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

// 组件引用
const messageToastRef = ref<{ pushMessage: (vo: any) => void } | null>(null)
const friendRequestToastRef = ref<{ pushFriendRequest: (...args: any[]) => void } | null>(null)

// 监听通知事件
onMounted(async () => {
  logWs('info', 'GlobalRealtimeBridge 组件已挂载')

  // 请求桌面通知权限
  await desktopNotify.requestPermission()

  // 初始化未读计数
  if (auth.isLoggedIn) {
    notificationStore.loadUnreadCount()
  }
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
  <MessageToast ref="messageToastRef" />
  <FriendRequestToast ref="friendRequestToastRef" />
</template>
