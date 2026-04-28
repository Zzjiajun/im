<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchNotifications,
  fetchNotificationUnreadCount,
  markNotificationAsRead,
  markAllNotificationsAsRead,
  deleteNotification,
  clearAllNotifications
} from '@/api/notification'
import type { NotificationVO, NotificationUnreadVO } from '@/types/api'

const notifications = ref<NotificationVO[]>([])
const unreadCount = ref(0)
const loading = ref(false)
const currentTab = ref<'all' | 'unread'>('all')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const err = ref('')

const filteredNotifications = computed(() => {
  if (currentTab.value === 'unread') {
    return notifications.value.filter(n => !n.isRead)
  }
  return notifications.value
})

async function loadNotifications() {
  loading.value = true
  err.value = ''
  try {
    const res = await fetchNotifications({
      isRead: currentTab.value === 'all' ? undefined : false,
      page: currentPage.value,
      size: pageSize.value
    })
    notifications.value = res
    total.value = res.length

    // 更新未读计数
    await loadUnreadCount()
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  try {
    const res: NotificationUnreadVO = await fetchNotificationUnreadCount()
    unreadCount.value = res.unreadCount
  } catch (e: unknown) {
    console.error('Failed to load unread count:', e)
  }
}

async function handleMarkAsRead(notification: NotificationVO) {
  if (notification.isRead) return

  try {
    await markNotificationAsRead(notification.id)
    notification.isRead = true
    notification.readAt = new Date().toISOString()
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    ElMessage.success('已标记为已读')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleMarkAllAsRead() {
  if (unreadCount.value === 0) return

  try {
    await markAllNotificationsAsRead()
    notifications.value.forEach(n => {
      n.isRead = true
      n.readAt = new Date().toISOString()
    })
    unreadCount.value = 0
    ElMessage.success('所有通知已标记为已读')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleDelete(notification: NotificationVO) {
  try {
    await deleteNotification(notification.id)
    notifications.value = notifications.value.filter(n => n.id !== notification.id)
    total.value--
    if (!notification.isRead) {
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    }
    ElMessage.success('通知已删除')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function handleClearAll() {
  if (notifications.value.length === 0) return

  try {
    await clearAllNotifications()
    notifications.value = []
    total.value = 0
    unreadCount.value = 0
    ElMessage.success('所有通知已清空')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

function getNotificationTypeText(type: string) {
  const typeMap: Record<string, string> = {
    'FRIEND_REQUEST': '好友申请',
    'FRIEND_ACCEPTED': '好友接受',
    'GROUP_INVITE': '群邀请',
    'GROUP_MEMBER_CHANGE': '群成员变化',
    'MENTION': '@消息',
    'SYSTEM_ANNOUNCEMENT': '系统公告'
  }
  return typeMap[type] || type
}

function formatDate(dateStr?: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadNotifications()
})
</script>

<template>
  <div class="notifications-container">
    <div class="page-header">
      <h1>通知中心</h1>
      <div class="header-actions">
        <el-button
          type="primary"
          @click="handleMarkAllAsRead"
          :disabled="unreadCount === 0"
        >
          标记全部已读 ({{ unreadCount }})
        </el-button>
        <el-button
          type="danger"
          plain
          @click="handleClearAll"
          :disabled="notifications.length === 0"
        >
          清空所有
        </el-button>
      </div>
    </div>

    <!-- 标签切换 -->
    <div class="tab-switch">
      <el-button-group>
        <el-button
          :type="currentTab === 'all' ? 'primary' : ''"
          @click="currentTab = 'all'"
        >
          全部 ({{ total }})
        </el-button>
        <el-button
          :type="currentTab === 'unread' ? 'primary' : ''"
          @click="currentTab = 'unread'"
        >
          未读 ({{ unreadCount }})
        </el-button>
      </el-button-group>
    </div>

    <!-- 通知列表 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>

    <el-alert
      v-else-if="err"
      type="error"
      :title="err"
      show-icon
      :closable="false"
    />

    <div v-else-if="filteredNotifications.length === 0" class="empty-state">
      <el-empty description="暂无通知" />
    </div>

    <div v-else class="notification-list">
      <div
        v-for="notification in filteredNotifications"
        :key="notification.id"
        class="notification-item"
        :class="{ unread: !notification.isRead }"
      >
        <div class="notification-icon">
          <el-avatar
            :src="notification.senderAvatar || '/default-avatar.png'"
            :size="40"
          >
            {{ notification.senderNickname?.[0] || '系' }}
          </el-avatar>
        </div>

        <div class="notification-content">
          <div class="notification-header">
            <span class="notification-type" :class="{ unread: !notification.isRead }">
              {{ getNotificationTypeText(notification.type) }}
            </span>
            <span class="notification-time">
              {{ formatDate(notification.createdAt) }}
            </span>
          </div>

          <div class="notification-title" :class="{ unread: !notification.isRead }">
            {{ notification.title }}
          </div>

          <div class="notification-body" :class="{ unread: !notification.isRead }">
            {{ notification.content }}
          </div>
        </div>

        <div class="notification-actions">
          <div class="unread-badge" v-if="!notification.isRead">
            未读
          </div>
          <el-button-group>
            <el-button
              v-if="!notification.isRead"
              type="primary"
              size="small"
              @click="handleMarkAsRead(notification)"
            >
              标记已读
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleDelete(notification)"
            >
              删除
            </el-button>
          </el-button-group>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notifications-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.tab-switch {
  margin-bottom: 20px;
}

.loading-container {
  padding: 40px;
}

.empty-state {
  padding: 60px 20px;
  text-align: center;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  background: white;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: all 0.3s ease;
}

.notification-item.unread {
  background: #f8f9ff;
  border-color: #d8e1ff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.notification-icon {
  flex-shrink: 0;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.notification-type {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.notification-type.unread {
  color: #409eff;
  font-weight: 600;
}

.notification-time {
  font-size: 12px;
  color: #909399;
}

.notification-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.notification-title.unread {
  color: #409eff;
}

.notification-body {
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
  word-break: break-word;
}

.notification-body.unread {
  color: #303133;
}

.notification-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.unread-badge {
  background: #409eff;
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

@media (max-width: 768px) {
  .notifications-container {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .notification-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .notification-actions {
    align-items: flex-start;
    width: 100%;
  }
}
</style>