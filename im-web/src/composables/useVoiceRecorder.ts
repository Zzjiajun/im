import { ref } from 'vue'

/**
 * 浏览器录音（MediaRecorder），用于发送语音消息。
 */
export function useVoiceRecorder() {
  const recording = ref(false)
  let mediaRecorder: MediaRecorder | null = null
  const chunks: Blob[] = []

  async function start(): Promise<void> {
    if (recording.value) return
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus'
      : 'audio/webm'
    mediaRecorder = new MediaRecorder(stream, { mimeType: mime })
    chunks.length = 0
    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data)
    }
    mediaRecorder.start(100)
    recording.value = true
  }

  function stop(): Promise<Blob | null> {
    if (!mediaRecorder || !recording.value) {
      recording.value = false
      return Promise.resolve(null)
    }
    const mr = mediaRecorder
    return new Promise((resolve) => {
      mr.onstop = () => {
        recording.value = false
        mr.stream.getTracks().forEach((t) => t.stop())
        mediaRecorder = null
        const type = mr.mimeType || 'audio/webm'
        const blob = new Blob(chunks, { type: type })
        chunks.length = 0
        resolve(blob.size > 0 ? blob : null)
      }
      mr.stop()
    })
  }

  function cancel(): void {
    if (!mediaRecorder) {
      recording.value = false
      return
    }
    chunks.length = 0
    mediaRecorder.stream.getTracks().forEach((t) => t.stop())
    mediaRecorder = null
    recording.value = false
  }

  return { recording, start, stop, cancel }
}
