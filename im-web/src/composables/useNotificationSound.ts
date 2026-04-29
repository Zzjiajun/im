/**
 * 新消息声音提示 composable
 * 使用 Web Audio API 生成简单的提示音，无需加载外部音频文件
 */
export function useNotificationSound() {
  let audioCtx: AudioContext | null = null

  function getContext(): AudioContext {
    if (!audioCtx) {
      audioCtx = new (window.AudioContext || (window as any).webkitAudioContext)()
    }
    return audioCtx
  }

  /** 播放新消息提示音（短促的 double-chime） */
  function playMessageSound() {
    try {
      const ctx = getContext()
      if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {})
      }
      const now = ctx.currentTime

      // 音符1：稍高
      const osc1 = ctx.createOscillator()
      osc1.type = 'sine'
      osc1.frequency.setValueAtTime(880, now) // A5
      osc1.frequency.setValueAtTime(1047, now + 0.06) // C6

      const gain1 = ctx.createGain()
      gain1.gain.setValueAtTime(0.12, now)
      gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.15)

      osc1.connect(gain1).connect(ctx.destination)
      osc1.start(now)
      osc1.stop(now + 0.15)

      // 音符2：稍低
      const osc2 = ctx.createOscillator()
      osc2.type = 'sine'
      osc2.frequency.setValueAtTime(660, now + 0.12) // E5

      const gain2 = ctx.createGain()
      gain2.gain.setValueAtTime(0.10, now + 0.12)
      gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.28)

      osc2.connect(gain2).connect(ctx.destination)
      osc2.start(now + 0.12)
      osc2.stop(now + 0.28)
    } catch {
      // 静默失败（浏览器可能禁止音频）
    }
  }

  /** 播放通话提示音（稍长） */
  function playCallSound() {
    try {
      const ctx = getContext()
      if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {})
      }
      const now = ctx.currentTime

      const osc = ctx.createOscillator()
      osc.type = 'sine'
      osc.frequency.setValueAtTime(600, now)
      osc.frequency.setValueAtTime(750, now + 0.3)
      osc.frequency.setValueAtTime(600, now + 0.6)
      osc.frequency.setValueAtTime(750, now + 0.9)

      const gain = ctx.createGain()
      gain.gain.setValueAtTime(0.08, now)
      gain.gain.setValueAtTime(0.08, now + 1.0)
      gain.gain.exponentialRampToValueAtTime(0.001, now + 1.3)

      osc.connect(gain).connect(ctx.destination)
      osc.start(now)
      osc.stop(now + 1.3)
    } catch {
      // 静默失败
    }
  }

  return {
    playMessageSound,
    playCallSound,
  }
}
