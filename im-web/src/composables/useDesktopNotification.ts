/**
 * 桌面通知 composable
 * 使用浏览器 Notification API 在标签页非激活时弹出系统通知
 */
export function useDesktopNotification() {
  let granted = false
  let attempted = false

  /** 请求通知权限 */
  async function requestPermission(): Promise<boolean> {
    if (attempted) return granted
    attempted = true
    if (!('Notification' in window)) {
      console.log('[DesktopNotify] 浏览器不支持 Notification API')
      return false
    }
    if (Notification.permission === 'granted') {
      granted = true
      return true
    }
    if (Notification.permission === 'denied') {
      console.log('[DesktopNotify] 通知权限已被拒绝')
      return false
    }
    try {
      const result = await Notification.requestPermission()
      granted = result === 'granted'
      return granted
    } catch {
      return false
    }
  }

  /** 发送桌面通知（仅在标签页未激活时弹出） */
  function notify(title: string, options?: { body?: string; icon?: string; tag?: string; onClick?: () => void }) {
    // 如果页面是激活状态，不打扰用户
    if (document.visibilityState === 'visible' && document.hasFocus()) {
      return
    }
    if (!granted) return
    try {
      const n = new Notification(title, {
        body: options?.body,
        icon: options?.icon,
        tag: options?.tag,
        silent: true, // 不播放系统声音，我们用自定义声音
      })
      if (options?.onClick) {
        n.onclick = () => {
          n.close()
          window.focus()
          options.onClick?.()
        }
      }
      // 自动关闭
      setTimeout(() => n.close(), 6000)
    } catch (e) {
      console.warn('[DesktopNotify] 发送通知失败:', e)
    }
  }

  /** 发送新消息桌面通知 */
  function notifyNewMessage(
    senderName: string,
    preview: string,
    avatarUrl?: string | null,
    onClick?: () => void,
  ) {
    notify(senderName, {
      body: preview,
      icon: avatarUrl || undefined,
      tag: 'im-new-message',
      onClick,
    })
  }

  /** 发送应用内通知（好友请求等） */
  function notifyAppNotification(
    title: string,
    body: string,
    icon?: string | null,
    onClick?: () => void,
  ) {
    notify(title, {
      body,
      icon: icon || undefined,
      tag: 'im-notification',
      onClick,
    })
  }

  return {
    requestPermission,
    notify,
    notifyNewMessage,
    notifyAppNotification,
  }
}
