import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as notificationApi from '@/api/notification'
import type { NotificationVO, NotificationUnreadVO } from '@/types/api'

export const useNotificationStore = defineStore('notification', () => {
  const notifications = ref<NotificationVO[]>([])
  const unreadCount = ref(0)
  const loading = ref(false)

  const hasUnread = computed(() => unreadCount.value > 0)

  async function loadUnreadCount() {
    try {
      const res: NotificationUnreadVO = await notificationApi.fetchNotificationUnreadCount()
      unreadCount.value = res.unreadCount
      return res.unreadCount
    } catch (e) {
      console.error('Failed to load unread count:', e)
      return 0
    }
  }

  async function loadNotifications(opts?: { isRead?: boolean; page?: number; size?: number }) {
    loading.value = true
    try {
      notifications.value = await notificationApi.fetchNotifications(opts)
      return notifications.value
    } catch (e) {
      console.error('Failed to load notifications:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  async function markAsRead(notificationId: number) {
    try {
      await notificationApi.markNotificationAsRead(notificationId)
      const notification = notifications.value.find(n => n.id === notificationId)
      if (notification && !notification.isRead) {
        notification.isRead = true
        notification.readAt = new Date().toISOString()
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    } catch (e) {
      console.error('Failed to mark notification as read:', e)
      throw e
    }
  }

  async function markAllAsRead() {
    try {
      await notificationApi.markAllNotificationsAsRead()
      notifications.value.forEach(n => {
        if (!n.isRead) {
          n.isRead = true
          n.readAt = new Date().toISOString()
        }
      })
      unreadCount.value = 0
    } catch (e) {
      console.error('Failed to mark all notifications as read:', e)
      throw e
    }
  }

  async function deleteNotification(notificationId: number) {
    try {
      await notificationApi.deleteNotification(notificationId)
      notifications.value = notifications.value.filter(n => n.id !== notificationId)
    } catch (e) {
      console.error('Failed to delete notification:', e)
      throw e
    }
  }

  async function clearAllNotifications() {
    try {
      await notificationApi.clearAllNotifications()
      notifications.value = []
      unreadCount.value = 0
    } catch (e) {
      console.error('Failed to clear all notifications:', e)
      throw e
    }
  }

  function addNotification(notification: NotificationVO) {
    // 添加到顶部，最新的在最前面
    notifications.value.unshift(notification)
    if (!notification.isRead) {
      unreadCount.value++
    }
  }

  function markNotificationAsReadById(notificationId: number) {
    const notification = notifications.value.find(n => n.id === notificationId)
    if (notification && !notification.isRead) {
      notification.isRead = true
      notification.readAt = new Date().toISOString()
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
  }

  return {
    notifications,
    unreadCount,
    loading,
    hasUnread,
    loadUnreadCount,
    loadNotifications,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    clearAllNotifications,
    addNotification,
    markNotificationAsReadById
  }
})