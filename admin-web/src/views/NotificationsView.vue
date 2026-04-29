<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElInput } from 'element-plus'
import * as api from '@/api/notificationApi'
import * as adminApi from '@/api/adminApi'
import type { NotificationVO } from '@/types'

const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const records = ref<NotificationVO[]>([])
const loading = ref(false)
const err = ref('')

// 通知类型筛选
const typeFilter = ref<string>('')
const readFilter = ref<string>('all') // all | unread | read

// 公告发布弹窗
const showAnnounceDialog = ref(false)
const announceTitle = ref('')
const announceContent = ref('')
const announceToAll = ref(true)
const announceUserIds = ref('')
const announceSubmitting = ref(false)

async function load() {
  loading.value = true
  err.value = ''
  try {
    const params: Record<string, unknown> = {
      page: page.value,
      size: size.value
    }
    if (readFilter.value === 'unread') {
      params.isRead = false
    } else if (readFilter.value === 'read') {
      params.isRead = true
    }
    if (typeFilter.value) {
      params.type = typeFilter.value
    }
    const res = await api.adminFetchNotifications(params)
    records.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function deleteNotification(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该通知？', '确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await adminApi.adminDeleteNotification(id)
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
  // 管理员清空需要输入目标用户 ID
  try {
    const { value: userId } = await ElMessageBox.prompt(
      '请输入要清空通知的用户 ID（留空则仅删除当前列表中的通知）：',
      '清空通知',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputType: 'text',
      }
    )
    if (userId && userId.trim()) {
      await adminApi.adminClearAllNotifications(userId.trim())
    } else {
      // 没有指定 userId，逐个删除当前列表中的通知
      for (const record of records.value) {
        await adminApi.adminDeleteNotification(record.id)
      }
    }
    records.value = []
    total.value = 0
    ElMessage.success('通知已清空')
  } catch (e: unknown) {
    if (e === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : String(e))
  }
}

async function submitAnnouncement() {
  if (!announceTitle.value.trim() || !announceContent.value.trim()) {
    ElMessage.warning('请输入公告标题和内容')
    return
  }
  announceSubmitting.value = true
  try {
    let targetIds: string[] | undefined
    if (!announceToAll.value && announceUserIds.value.trim()) {
      targetIds = announceUserIds.value.split(/[,，\s]+/).filter(Boolean)
    }
    await adminApi.createAnnouncement(
      announceTitle.value.trim(),
      announceContent.value.trim(),
      targetIds
    )
    ElMessage.success('公告发布成功')
    showAnnounceDialog.value = false
    announceTitle.value = ''
    announceContent.value = ''
    announceUserIds.value = ''
    // 刷新通知列表
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : String(e))
  } finally {
    announceSubmitting.value = false
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
})
</script>

<template>
  <div>
    <h2 class="page-title">通知中心管理</h2>

    <!-- 筛选与操作栏 -->
    <el-row :gutter="12" style="margin-bottom: 20px; align-items: center;">
      <el-col :span="4">
        <el-select v-model="readFilter" placeholder="阅读状态" clearable @change="load">
          <el-option label="全部" value="all" />
          <el-option label="未读" value="unread" />
          <el-option label="已读" value="read" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-select v-model="typeFilter" placeholder="通知类型" clearable @change="load">
          <el-option label="全部" value="" />
          <el-option label="好友申请" value="FRIEND_REQUEST" />
          <el-option label="好友接受" value="FRIEND_ACCEPTED" />
          <el-option label="群邀请" value="GROUP_INVITE" />
          <el-option label="群成员变化" value="GROUP_MEMBER_CHANGE" />
          <el-option label="@消息" value="MENTION" />
          <el-option label="系统公告" value="SYSTEM_ANNOUNCEMENT" />
        </el-select>
      </el-col>
      <el-col :span="6" :offset="10" style="text-align: right;">
        <el-button type="primary" @click="showAnnounceDialog = true">
          发布公告
        </el-button>
        <el-button type="danger" plain @click="clearAllNotifications" :disabled="records.length === 0">
          清空通知
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
          <el-button type="danger" size="small" @click="deleteNotification(row.id)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页（后端返回 { records, total } 分页数据） -->
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

    <!-- 发布公告弹窗 -->
    <el-dialog v-model="showAnnounceDialog" title="发布系统公告" width="500px">
      <el-form label-width="80px">
        <el-form-item label="公告标题">
          <el-input v-model="announceTitle" placeholder="输入公告标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="公告内容">
          <el-input v-model="announceContent" type="textarea" :rows="4" placeholder="输入公告内容" maxlength="2000" />
        </el-form-item>
        <el-form-item label="发送范围">
          <el-radio-group v-model="announceToAll">
            <el-radio :value="true">全部用户</el-radio>
            <el-radio :value="false">指定用户</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!announceToAll" label="用户ID">
          <el-input v-model="announceUserIds" placeholder="多个 ID 用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAnnounceDialog = false">取消</el-button>
        <el-button type="primary" :loading="announceSubmitting" @click="submitAnnouncement">
          发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
}
</style>
