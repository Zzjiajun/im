import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import AgoraRTC, { type IAgoraRTCClient, type IMicrophoneAudioTrack } from 'agora-rtc-sdk-ng'
import * as callApi from '@/api/call'
import { useAuthStore } from '@/stores/auth'
import { i18n } from '@/i18n'
import type { SnowflakeId, VoiceCallVO, WsEnvelope } from '@/types/api'

type CallPhase = 'idle' | 'incoming' | 'outgoing' | 'connecting' | 'connected'

export const useVoiceCallStore = defineStore('voiceCall', () => {
  const auth = useAuthStore()

  const currentCall = ref<VoiceCallVO | null>(null)
  const phase = ref<CallPhase>('idle')
  const muted = ref(false)
  const remoteJoined = ref(false)
  const bootstrapped = ref(false)

  let client: IAgoraRTCClient | null = null
  let localTrack: IMicrophoneAudioTrack | null = null
  let joinedCallId: string | null = null
  let ringingTimeoutTimer: ReturnType<typeof setTimeout> | null = null
  let bootstrapPromise: Promise<void> | null = null

  const busy = computed(() => phase.value !== 'idle' && currentCall.value != null)
  const incoming = computed(() => phase.value === 'incoming')
  const connected = computed(() => phase.value === 'connected')

  function isSelfCaller(vo: VoiceCallVO) {
    return String(auth.user?.id ?? '') === String(vo.callerUserId)
  }

  function clearRingingTimer() {
    if (ringingTimeoutTimer) {
      clearTimeout(ringingTimeoutTimer)
      ringingTimeoutTimer = null
    }
  }

  function resetState() {
    clearRingingTimer()
    currentCall.value = null
    phase.value = 'idle'
    muted.value = false
    remoteJoined.value = false
  }

  function applyCallState(vo: VoiceCallVO | null) {
    currentCall.value = vo
    if (!vo) {
      phase.value = 'idle'
      return
    }
    if (vo.status === 'ACCEPTED') {
      phase.value = joinedCallId === vo.callId ? 'connected' : 'connecting'
      return
    }
    if (vo.status === 'RINGING') {
      phase.value = isSelfCaller(vo) ? 'outgoing' : 'incoming'
      return
    }
    phase.value = 'idle'
  }

  function ensureClient() {
    if (client) return client
    client = AgoraRTC.createClient({ mode: 'rtc', codec: 'vp8' })
    client.on('user-published', async (user, mediaType) => {
      if (!client) return
      await client.subscribe(user, mediaType)
      if (mediaType === 'audio' && user.audioTrack) {
        user.audioTrack.play()
        remoteJoined.value = true
      }
    })
    client.on('user-unpublished', (user, mediaType) => {
      if (mediaType === 'audio' && user.audioTrack) {
        user.audioTrack.stop()
      }
      remoteJoined.value = false
    })
    client.on('user-left', () => {
      remoteJoined.value = false
    })
    return client
  }

  function assertCallEnvironment() {
    const hostname = window.location.hostname
    const localHosts = new Set(['localhost', '127.0.0.1', '::1'])
    const secureOk = window.isSecureContext || localHosts.has(hostname)
    if (!secureOk) {
      throw new Error(String(i18n.global.t('chat.callSecureContextRequired')))
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error(String(i18n.global.t('chat.voiceMicDenied')))
    }
  }

  function normalizeAgoraJoinUid(uid: string): string | number {
    if (/^\d+$/.test(uid)) {
      const n = Number(uid)
      if (Number.isSafeInteger(n) && n >= 0) {
        return n
      }
    }
    return uid
  }

  async function preflightMicrophoneAccess() {
    assertCallEnvironment()
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      for (const track of stream.getTracks()) {
        track.stop()
      }
    } catch (error) {
      const message =
        error instanceof Error && /permission|denied|NotAllowed|NotFound|device/i.test(error.message)
          ? String(i18n.global.t('chat.voiceMicDenied'))
          : error instanceof Error
            ? error.message
            : String(error)
      throw new Error(message)
    }
  }

  async function leaveAgora() {
    try {
      if (localTrack) {
        localTrack.stop()
        localTrack.close()
      }
      localTrack = null
      if (client && joinedCallId) {
        await client.leave()
      }
    } catch {
      /* ignore */
    } finally {
      joinedCallId = null
      remoteJoined.value = false
      muted.value = false
    }
  }

  async function joinAgora(vo: VoiceCallVO) {
    if (joinedCallId === vo.callId) {
      phase.value = 'connected'
      return
    }
    assertCallEnvironment()
    phase.value = 'connecting'
    const rtc = await callApi.fetchAgoraVoiceToken(vo.callId)
    const rtcClient = ensureClient()
    await rtcClient.join(rtc.appId, rtc.channelName, rtc.token || null, normalizeAgoraJoinUid(rtc.uid))
    try {
      localTrack = await AgoraRTC.createMicrophoneAudioTrack()
    } catch (error) {
      const message =
        error instanceof Error && /permission|denied|NotAllowed/i.test(error.message)
          ? String(i18n.global.t('chat.voiceMicDenied'))
          : error instanceof Error
            ? error.message
            : String(error)
      throw new Error(message)
    }
    await rtcClient.publish([localTrack])
    joinedCallId = vo.callId
    phase.value = 'connected'
  }

  async function syncCurrentCall() {
    if (!auth.token) {
      await leaveAgora()
      resetState()
      bootstrapped.value = false
      return
    }

    const vo = await callApi.fetchCurrentVoiceCall()
    clearRingingTimer()

    if (!vo) {
      await leaveAgora()
      resetState()
      bootstrapped.value = true
      return
    }

    applyCallState(vo)

    if (vo.status === 'ACCEPTED') {
      try {
        await joinAgora(vo)
      } catch {
        await leaveAgora()
        resetState()
      }
    } else if (vo.status === 'RINGING' && isSelfCaller(vo)) {
      ringingTimeoutTimer = setTimeout(async () => {
        if (!currentCall.value || currentCall.value.callId !== vo.callId || phase.value !== 'outgoing') return
        try {
          await callApi.endVoiceCall(vo.callId)
        } catch {
          /* ignore */
        } finally {
          await leaveAgora()
          resetState()
        }
      }, 45_000)
    }

    bootstrapped.value = true
  }

  async function bootstrap() {
    if (bootstrapPromise) return bootstrapPromise
    bootstrapPromise = syncCurrentCall()
      .catch(async () => {
        await leaveAgora()
        resetState()
      })
      .finally(() => {
        bootstrapped.value = true
        bootstrapPromise = null
      })
    return bootstrapPromise
  }

  async function start(conversationId: SnowflakeId) {
    if (busy.value) {
      throw new Error('当前已有语音通话')
    }
    await preflightMicrophoneAccess()
    const vo = await callApi.startVoiceCall({ conversationId })
    clearRingingTimer()
    applyCallState(vo)
    ringingTimeoutTimer = setTimeout(async () => {
      if (!currentCall.value || currentCall.value.callId !== vo.callId || phase.value !== 'outgoing') return
      try {
        await callApi.endVoiceCall(vo.callId)
      } catch {
        /* ignore */
      } finally {
        await leaveAgora()
        resetState()
      }
    }, 45_000)
  }

  async function accept() {
    if (!currentCall.value) return
    clearRingingTimer()
    await preflightMicrophoneAccess()
    const vo = await callApi.acceptVoiceCall(currentCall.value.callId)
    applyCallState(vo)
    try {
      await joinAgora(vo)
    } catch (e) {
      await leaveAgora()
      throw e
    }
  }

  async function reject() {
    if (!currentCall.value) return
    const callId = currentCall.value.callId
    await callApi.rejectVoiceCall(callId)
    await leaveAgora()
    resetState()
  }

  async function hangup() {
    if (!currentCall.value) return
    const callId = currentCall.value.callId
    try {
      await callApi.endVoiceCall(callId)
    } finally {
      await leaveAgora()
      resetState()
    }
  }

  async function toggleMute() {
    if (!localTrack) return
    muted.value = !muted.value
    await localTrack.setEnabled(!muted.value)
  }

  async function handleWsEvent(env: WsEnvelope<unknown>) {
    const data = env.data
    if (!data || typeof data !== 'object') return false
    const vo = data as VoiceCallVO
    if (!('callId' in vo)) return false

    if (env.event === 'CALL_INVITE') {
      if (busy.value) {
        await callApi.rejectVoiceCall(vo.callId).catch(() => undefined)
        return true
      }
      clearRingingTimer()
      applyCallState(vo)
      return true
    }

    if (env.event === 'CALL_ACCEPTED') {
      clearRingingTimer()
      applyCallState(vo)
      if (isSelfCaller(vo)) {
        try {
          await joinAgora(vo)
        } catch {
          await leaveAgora()
        }
      }
      return true
    }

    if (env.event === 'CALL_REJECTED' || env.event === 'CALL_ENDED') {
      if (!currentCall.value || currentCall.value.callId !== vo.callId) {
        return true
      }
      await leaveAgora()
      resetState()
      return true
    }

    return false
  }

  async function teardown() {
    await leaveAgora()
    resetState()
    bootstrapped.value = false
  }

  return {
    currentCall,
    phase,
    muted,
    remoteJoined,
    busy,
    incoming,
    connected,
    bootstrapped,
    bootstrap,
    syncCurrentCall,
    start,
    accept,
    reject,
    hangup,
    toggleMute,
    handleWsEvent,
    teardown,
  }
})
