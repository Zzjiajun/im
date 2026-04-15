import { http, unwrap } from './http'
import type { AgoraRtcTokenVO, StartVoiceCallRequest, VoiceCallVO } from '@/types/api'

export function fetchCurrentVoiceCall() {
  return unwrap<VoiceCallVO | null>(http.get('/calls/voice/current'))
}

export function startVoiceCall(body: StartVoiceCallRequest) {
  return unwrap<VoiceCallVO>(http.post('/calls/voice/start', body))
}

export function acceptVoiceCall(callId: string) {
  return unwrap<VoiceCallVO>(http.post(`/calls/voice/${callId}/accept`))
}

export function rejectVoiceCall(callId: string) {
  return unwrap<VoiceCallVO>(http.post(`/calls/voice/${callId}/reject`))
}

export function endVoiceCall(callId: string) {
  return unwrap<VoiceCallVO>(http.post(`/calls/voice/${callId}/end`))
}

export function fetchAgoraVoiceToken(callId: string) {
  return unwrap<AgoraRtcTokenVO>(http.get(`/calls/voice/${callId}/agora-token`))
}
