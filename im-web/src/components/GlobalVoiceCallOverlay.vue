<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useVoiceCallStore } from '@/stores/voiceCall'

const { t } = useI18n()
const auth = useAuthStore()
const chat = useChatStore()
const voiceCall = useVoiceCallStore()
const router = useRouter()
const route = useRoute()
const actionError = ref('')
const elapsedSeconds = ref(0)
let errorTimer: ReturnType<typeof setTimeout> | null = null
let durationTimer: ReturnType<typeof setInterval> | null = null

function resolveAvatarUrl(url?: string | null): string | null {
  const u = String(url ?? '').trim()
  if (!u) return null
  if (u.startsWith('http://') || u.startsWith('https://') || u.startsWith('//')) return u
  if (u.startsWith('/')) return u
  const base = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')
  return `${base}/${u.replace(/^\/+/, '')}`
}

function initial(name?: string | null) {
  const s = (name || '?').trim()
  return s.slice(0, 1).toUpperCase()
}

const isIncoming = computed(() => voiceCall.phase === 'incoming')
const peerName = computed(() => {
  const call = voiceCall.currentCall
  if (!call) return ''
  if (String(auth.user?.id ?? '') === String(call.callerUserId)) {
    return call.calleeNickname || `用户 #${call.calleeUserId}`
  }
  return call.callerNickname || `用户 #${call.callerUserId}`
})

const peerAvatar = computed(() => {
  const call = voiceCall.currentCall
  if (!call) return null
  if (String(auth.user?.id ?? '') === String(call.callerUserId)) {
    return resolveAvatarUrl(call.calleeAvatar)
  }
  return resolveAvatarUrl(call.callerAvatar)
})

const statusText = computed(() => {
  if (voiceCall.phase === 'incoming') return t('chat.callIncoming')
  if (voiceCall.phase === 'outgoing') return t('chat.callDialing')
  if (voiceCall.phase === 'connecting') return t('chat.callConnecting')
  return voiceCall.remoteJoined ? t('chat.callConnected') : t('chat.callWaitingPeer')
})

const durationText = computed(() => {
  if (!voiceCall.connected) return ''
  const total = elapsedSeconds.value
  const hours = Math.floor(total / 3600)
  const mins = Math.floor((total % 3600) / 60)
  const secs = total % 60
  if (hours > 0) {
    return [hours, mins, secs].map((n) => String(n).padStart(2, '0')).join(':')
  }
  return [mins, secs].map((n) => String(n).padStart(2, '0')).join(':')
})

function resetDurationTimer() {
  if (durationTimer) {
    clearInterval(durationTimer)
    durationTimer = null
  }
  elapsedSeconds.value = 0
}

function syncDuration() {
  const answeredAt = voiceCall.currentCall?.answeredAt
  if (!answeredAt) {
    elapsedSeconds.value = 0
    return
  }
  const startedAt = new Date(answeredAt).getTime()
  if (Number.isNaN(startedAt)) {
    elapsedSeconds.value = 0
    return
  }
  elapsedSeconds.value = Math.max(0, Math.floor((Date.now() - startedAt) / 1000))
}

function showActionError(error: unknown) {
  actionError.value = error instanceof Error ? error.message : String(error)
  if (errorTimer) clearTimeout(errorTimer)
  errorTimer = setTimeout(() => {
    actionError.value = ''
  }, 3000)
}

function openConversation() {
  const conversationId = voiceCall.currentCall?.conversationId
  if (!conversationId) return
  if (route.name === 'chat') return
  void router.push({ name: 'chat', query: { openConv: conversationId } })
}

function idEq(a?: string | null, b?: string | null) {
  return String(a ?? '') === String(b ?? '')
}

async function onAccept() {
  try {
    await voiceCall.accept()
  } catch (error) {
    showActionError(error)
  }
}

async function onReject() {
  try {
    await voiceCall.reject()
  } catch (error) {
    showActionError(error)
  }
}

async function onHangup() {
  try {
    await voiceCall.hangup()
  } catch (error) {
    showActionError(error)
  }
}

async function onToggleMute() {
  try {
    await voiceCall.toggleMute()
  } catch (error) {
    showActionError(error)
  }
}

onUnmounted(() => {
  if (errorTimer) clearTimeout(errorTimer)
  resetDurationTimer()
})

watch(
  () => [voiceCall.connected, voiceCall.currentCall?.answeredAt] as const,
  ([connected]) => {
    resetDurationTimer()
    if (!connected) return
    syncDuration()
    durationTimer = setInterval(syncDuration, 1000)
  },
  { immediate: true }
)

watch(
  () => voiceCall.currentCall?.conversationId,
  async (conversationId) => {
    if (!conversationId || route.name !== 'chat') return
    if (!chat.conversations.some((c) => idEq(String(c.conversationId), String(conversationId)))) {
      try {
        await chat.loadConversations()
      } catch {
        return
      }
    }
    if (!idEq(String(chat.activeId ?? ''), String(conversationId))) {
      try {
        await chat.selectConversation(conversationId)
      } catch {
        /* ignore */
      }
    }
  }
)
</script>

<template>
  <div v-if="voiceCall.currentCall" :class="isIncoming ? 'call-mask' : 'call-float-wrap'">
    <div class="call-panel" :class="{ incoming: isIncoming, floating: !isIncoming }">
      <div class="call-head">
        <span>{{ t('chat.voiceCall') }}</span>
        <button
          v-if="!isIncoming && route.name !== 'chat'"
          type="button"
          class="jump-btn"
          @click="openConversation"
        >
          {{ t('chat.openCurrentChat') }}
        </button>
      </div>

      <div class="call-body">
        <div class="call-avatar">
          <img v-if="peerAvatar" :src="peerAvatar" alt="" />
          <template v-else>{{ initial(peerName) }}</template>
        </div>
        <div class="call-name">{{ peerName }}</div>
        <div class="call-status">{{ statusText }}</div>
        <div v-if="durationText" class="call-duration">{{ durationText }}</div>
        <div v-if="actionError" class="call-error">{{ actionError }}</div>
        <div class="call-actions">
          <button
            v-if="voiceCall.incoming"
            type="button"
            class="wx-btn-primary"
            @click="onAccept"
          >
            {{ t('chat.callAccept') }}
          </button>
          <button
            v-if="voiceCall.incoming"
            type="button"
            class="btn-warn"
            @click="onReject"
          >
            {{ t('chat.callReject') }}
          </button>
          <button
            v-if="!voiceCall.incoming && voiceCall.connected"
            type="button"
            class="info-btn"
            @click="onToggleMute"
          >
            {{ voiceCall.muted ? t('chat.callUnmute') : t('chat.callMute') }}
          </button>
          <button
            v-if="!voiceCall.incoming"
            type="button"
            class="btn-warn"
            @click="onHangup"
          >
            {{ t('chat.callHangup') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.call-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.35);
  backdrop-filter: blur(2px);
}

.call-float-wrap {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 1200;
}

.call-panel {
  width: min(360px, calc(100vw - 24px));
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.2);
  overflow: hidden;
}

.call-panel.floating {
  border: 1px solid #d7e3df;
}

.call-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #eef3f2;
  font-weight: 700;
}

.jump-btn {
  border: 0;
  background: transparent;
  color: #17a289;
  cursor: pointer;
  font-size: 13px;
}

.call-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 22px 18px 20px;
}

.call-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #18b39b, #1d9dd8);
  color: #fff;
  font-size: 30px;
  font-weight: 700;
  overflow: hidden;
}

.call-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.call-name {
  font-size: 20px;
  font-weight: 700;
  color: #132222;
}

.call-status {
  font-size: 14px;
  color: #6d7e7b;
}

.call-duration {
  font-size: 24px;
  font-weight: 700;
  color: #132222;
  font-variant-numeric: tabular-nums;
}

.call-error {
  max-width: 100%;
  color: #d14343;
  font-size: 13px;
  text-align: center;
}

.call-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 4px;
}
</style>
