<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElBadge, ElPopover } from 'element-plus'
import { useNotificationStore } from '@/stores/notification'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const notificationStore = useNotificationStore()
const authStore = useAuthStore()

const popoverVisible = ref(false)

let notificationListener: EventListener | null = null
let reconnectTimer: number | null = null

const unreadCount = computed(() => notificationStore.unreadCount)
const hasUnread = computed(() => notificationStore.hasUnread)

function handleNotificationClick() {
  router.push('/notifications')
}

function handlePopoverShow() {
  // 加载未读通知
  notificationStore.loadNotifications({ isRead: false, size: 10 })
}

onMounted(async () => {
  // 初始化未读计数
  await notificationStore.loadUnreadCount()

  // 监听WebSocket通知事件
  notificationListener = (event: Event) => {
    const customEvent = event as CustomEvent
    if (customEvent.detail && typeof customEvent.detail === 'object') {
      // 有新通知，更新计数
      notificationStore.loadUnreadCount()
    }
  }

  window.addEventListener('im-notification', notificationListener)

  // 定时同步未读计数（30秒一次）
  reconnectTimer = window.setInterval(() => {
    if (authStore.isLoggedIn) {
      notificationStore.loadUnreadCount()
    }
  }, 30000)
})

onUnmounted(() => {
  if (notificationListener) {
    window.removeEventListener('im-notification', notificationListener)
  }
  if (reconnectTimer) {
    clearInterval(reconnectTimer)
  }
})

// 获取通知图标
function getNotificationIcon() {
  return 'Bell'
}
</script>

<template>
  <div class="notification-bell">
    <ElPopover
      :visible="popoverVisible"
      placement="bottom"
      width="300"
      trigger="click"
      popper-class="notification-popover"
      @show="handlePopoverShow"
    >
      <template #reference>
        <ElBadge
          :value="unreadCount"
          :hidden="!hasUnread"
          :max="99"
          class="notification-badge"
        >
          <el-button
            type="text"
            size="large"
            @click="handleNotificationClick"
            :style="{ color: hasUnread ? '#409eff' : '#666' }"
          >
            <el-icon><component :is="getNotificationIcon()" /></el-icon>
          </el-button>
        </ElBadge>
      </template>

      <!-- 通知弹窗内容 -->
      <div class="notification-popover-content">
        <div class="popover-header">
          <h3>通知中心</h3>
          <el-button
            text
            size="small"
            @click="handleNotificationClick"
          >
            查看全部
          </el-button>
        </div>

        <div v-if="notificationStore.loading" class="loading">
          <el-skeleton :rows="3" animated />
        </div>

        <div v-else-if="notificationStore.notifications.length === 0" class="empty">
          <el-empty description="暂无未读通知" />
        </div>

        <div v-else class="notification-list">
          <div
            v-for="notification in notificationStore.notifications"
            :key="notification.id"
            class="notification-item"
            @click="handleNotificationClick"
          >
            <div class="notification-icon">
              <el-avatar :size="32" :src="notification.senderAvatar">
                {{ notification.senderNickname?.[0] || '系' }}
              </el-avatar>
            </div>

            <div class="notification-content">
              <div class="notification-title">{{ notification.title }}</div>
              <div class="notification-desc">{{ notification.content }}</div>
              <div class="notification-time">
                {{ new Date(notification.createdAt || '').toLocaleString() }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </ElPopover>
  </div>
</template>

<style scoped>
.notification-bell {
  display: inline-block;
}

.notification-badge {
  margin-right: 8px;
}

.notification-popover-content {
  max-height: 400px;
  overflow-y: auto;
}

.popover-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
  margin-bottom: 12px;
}

.popover-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.loading {
  padding: 20px;
}

.empty {
  padding: 20px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.notification-item:hover {
  background-color: #f5f7fa;
}

.notification-icon {
  flex-shrink: 0;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notification-time {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

:deep(.notification-popover) {
  border: none;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

:deep(.el-popover__title) {
  display: none;
}
</style>