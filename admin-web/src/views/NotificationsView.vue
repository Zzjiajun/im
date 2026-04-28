<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/notificationApi'
import * as adminApi from '@/api/adminApi'
import type { NotificationVO, NotificationUnreadVO } from '@/types'

const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const records = ref<NotificationVO[]>([])
const loading = ref(false)
const err = ref('')

const unreadCount = ref(0)
const unreadLoading = ref(false)

async function load() {
  loading.value = true
  err.value = ''
  try {
    const res = await api.fetchNotifications({
      isRead: false,
      page: page.value,
      size: size.value
    })
    total.value = res.length
    records.value = res
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  unreadLoading.value = true
  try {
    const res = await api.fetchNotificationUnreadCount()
    unreadCount.value = res.unreadCount
  } catch (e: unknown) {
    console.error('Failed to load unread count:', e)
  } finally {
    unreadLoading.value = false
  }
}

async function markAsRead(id: number) {
  try {
    await api.markNotificationAsRead(id)
    const record = records.value.find(r => r.id === id)
    if (record) {
      record.isRead = true
      record.readAt = new Date().toISOString()
    }
    unreadCount.value--
    ElMessage.success('已标记为已读')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function markAllAsRead() {
  if (unreadCount.value === 0) return

  try {
    await ElMessageBox.confirm(`确认标记所有 ${unreadCount.value} 条通知为已读？`, '确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })

    await api.markAllNotificationsAsRead()
    records.value.forEach(record => {
      record.isRead = true
      record.readAt = new Date().toISOString()
    })
    unreadCount.value = 0
    ElMessage.success('所有通知已标记为已读')
  } catch (e: unknown) {
    if (e === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function deleteNotification(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该通知？', '确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })

    await api.deleteNotification(id)
    records.value = records.value.filter(r => r.id !== id)
    total.value--
    ElMessage.success('通知已删除')
  } catch (e: unknown) {
    if (e === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function clearAllNotifications() {
  if (records.value.length === 0) return

  try {
    await ElMessageBox.confirm(`确认清空所有 ${records.value.length} 条通知？`, '确认', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })

    await api.clearAllNotifications()
    records.value = []
    total.value = 0
    unreadCount.value = 0
    ElMessage.success('所有通知已清空')
  } catch (e: unknown) {
    if (e === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

function getTypeText(type: string) {
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

onMounted(async () => {
  await load()
  await loadUnreadCount()
})
</script>

<template>
  <div>
    <h2 class="page-title">通知中心管理</h2>

    <!-- 统计信息 -->
    <el-row :gutter="16" style="margin-bottom: 20px;">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="总通知数" :value="total" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="未读通知" :value="unreadCount">
            <template #suffix>
              <el-button
                type="primary"
                size="small"
                @click="markAllAsRead"
                :disabled="unreadCount === 0"
              >
                标记全部已读
              </el-button>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="已读通知" :value="total - unreadCount" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-button
          type="danger"
          plain
          @click="clearAllNotifications"
          :disabled="records.length === 0"
        >
          清空所有通知
        </el-button>
      </el-col>
    </el-row>

    <!-- 通知列表 -->
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-alert v-else-if="err" type="error" :title="err" show-icon :closable="false" />

    <el-table v-else :data="records" style="width: 100%">
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.isRead ? '' : 'danger'">
            {{ getTypeText(row.type) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="title" label="标题" width="200">
        <template #default="{ row }">
          <span :style="{ color: row.isRead ? '#666' : '#333', fontWeight: row.isRead ? 'normal' : 'bold' }">
            {{ row.title }}
          </span>
        </template>
      </el-table-column>

      <el-table-column prop="content" label="内容">
        <template #default="{ row }">
          <span :style="{ color: row.isRead ? '#666' : '#333' }">
            {{ row.content }}
          </span>
        </template>
      </el-table-column>

      <el-table-column prop="senderNickname" label="发送者" width="120">
        <template #default="{ row }">
          {{ row.senderNickname || '系统' }}
        </template>
      </el-table-column>

      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isRead ? 'success' : 'warning'">
            {{ row.isRead ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.isRead"
            type="primary"
            size="small"
            @click="markAsRead(row.id)"
          >
            标记已读
          </el-button>
          <el-button
            type="danger"
            size="small"
            @click="deleteNotification(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div style="margin-top: 20px; text-align: right;">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
}
</style>