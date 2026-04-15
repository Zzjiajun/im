<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useVoiceCallStore } from '@/stores/voiceCall'
import { useRealtimeStore } from '@/stores/realtime'
import {
  connectStomp,
  disconnectStomp,
  isMessagePayload,
} from '@/composables/useStomp'
import { resolveWebSocketUrl } from '@/utils/wsUrl'
import type { RecallWsPayload, SnowflakeId, WsEnvelope } from '@/types/api'

const auth = useAuthStore()
const chat = useChatStore()
const voiceCall = useVoiceCallStore()
const realtime = useRealtimeStore()
const wsUrl = resolveWebSocketUrl()

function idEq(a: SnowflakeId | null | undefined, b: SnowflakeId | null | undefined) {
  if (a == null || b == null) return false
  return String(a) === String(b)
}

async function handleWs(env: WsEnvelope<unknown>) {
  if (await voiceCall.handleWsEvent(env)) {
    return
  }
  if (env.event === 'MESSAGE' && isMessagePayload(env.data)) {
    void chat.applyWsPayload(env.data)
    return
  }
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
  if (env.event === 'TYPING' && env.data && typeof env.data === 'object') {
    window.dispatchEvent(new CustomEvent('im-typing', { detail: env.data }))
    return
  }
  if (env.event === 'MESSAGE_PINNED' && isMessagePayload(env.data)) {
    const vo = env.data
    if (idEq(vo.conversationId, chat.activeId)) {
      window.dispatchEvent(new CustomEvent('im-pinned', { detail: vo }))
    }
  }
}

function bindWs(token: string | null) {
  disconnectStomp()
  if (!token || !wsUrl) {
    realtime.setStatus('none')
    return
  }
  realtime.setStatus('offline')
  connectStomp(wsUrl, token, handleWs, {
    onConnected: () => {
      realtime.setStatus('live')
      const id = chat.activeId
      if (id) void chat.syncNewerMessages(id)
    },
    onDisconnected: () => {
      realtime.setStatus('offline')
    },
  })
}

onMounted(() => {
  bindWs(auth.token)
})

watch(
  () => auth.token,
  (token) => {
    bindWs(token)
  }
)
</script>

<template></template>
