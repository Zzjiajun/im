import { http, unwrap } from './http'
import type {
  NotificationVO,
  NotificationUnreadVO,
  NotificationListRequest,
  SnowflakeId,
} from '@/types'

export function fetchNotifications(opts?: NotificationListRequest) {
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

// 管理员通知管理接口
export function adminFetchNotifications(
  opts?: NotificationListRequest & {
    userId?: SnowflakeId;
    type?: string;
  }
) {
  return unwrap<NotificationVO[]>(
    http.get('/admin/notifications', {
      params: {
        ...(opts?.userId ? { userId: opts.userId } : {}),
        ...(opts?.type ? { type: opts.type } : {}),
        ...(opts?.isRead != null ? { isRead: opts.isRead } : {}),
        ...(opts?.page != null ? { page: opts.page } : {}),
        ...(opts?.size != null ? { size: opts.size } : {}),
      },
    })
  )
}

export function adminDeleteNotification(notificationId: number) {
  return unwrap<void>(http.delete(`/admin/notifications/${notificationId}`))
}

export function adminClearAllNotifications(userId: SnowflakeId) {
  return unwrap<void>(http.delete(`/admin/notifications/user/${userId}/clear`))
}