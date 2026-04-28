import { http, unwrap } from './http'
import type {
  NotificationVO,
  NotificationUnreadVO,
} from '@/types/api'

export function fetchNotifications(
  opts?: { isRead?: boolean; page?: number; size?: number }
) {
  return unwrap<NotificationVO[]>(
    http.get('/notifications', {
      params: {
        ...(opts?.isRead != null ? { isRead: opts.isRead } : {}),
        ...(opts?.page != null ? { page: opts.page } : {}),
        ...(opts?.size != null ? { size: opts.size } : {}),
      },
    })
  )
}

export function fetchNotificationUnreadCount() {
  return unwrap<NotificationUnreadVO>(http.get('/notifications/unread'))
}

export function markNotificationAsRead(notificationId: number) {
  return unwrap<void>(http.post(`/notifications/${notificationId}/read`))
}

export function markAllNotificationsAsRead() {
  return unwrap<void>(http.post('/notifications/read-all'))
}

export function deleteNotification(notificationId: number) {
  return unwrap<void>(http.delete(`/notifications/${notificationId}`))
}

export function clearAllNotifications() {
  return unwrap<void>(http.delete('/notifications/clear'))
}