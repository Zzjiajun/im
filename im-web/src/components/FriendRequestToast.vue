<template>
  <Teleport to="body">
    <TransitionGroup name="req-toast" tag="div" class="friend-request-toasts">
      <div
        v-for="req in requests"
        :key="req.id"
        class="req-toast"
      >
        <div class="req-avatar">
          <img v-if="req.avatar" :src="req.avatar" alt="" @error="onAvatarError" />
          <span v-else class="req-avatar-placeholder">{{ (req.nickname || '?')[0] }}</span>
        </div>
        <div class="req-body">
          <div class="req-title">好友申请</div>
          <div class="req-desc">{{ req.nickname }} 请求添加你为好友</div>
          <div v-if="req.remark" class="req-remark">验证信息: {{ req.remark }}</div>
        </div>
        <div class="req-actions">
          <button
            class="req-btn accept"
            :disabled="req.processing"
            @click="accept(req)"
          >
            {{ req.processing ? '...' : '同意' }}
          </button>
          <button
            class="req-btn reject"
            :disabled="req.processing"
            @click="reject(req)"
          >
            {{ req.processing ? '...' : '拒绝' }}
          </button>
        </div>
        <button class="req-close" @click="dismiss(req.id)">×</button>
      </div>
    </TransitionGroup>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import * as friendApi from '@/api/friend'
import type { SnowflakeId } from '@/types/api'

interface FriendRequestItem {
  id: string
  friendRequestId: SnowflakeId
  fromUserId: SnowflakeId
  nickname: string
  avatar?: string | null
  remark?: string | null
  processing?: boolean
  timerId?: number
}

const requests = ref<FriendRequestItem[]>([])
const TOAST_DURATION = 15000 // 15秒自动消失
const MAX_REQUESTS = 3

/** 收到好友申请时从外部调用 */
function pushFriendRequest(requestId: SnowflakeId, fromUserId: SnowflakeId, nickname: string, avatar?: string | null, remark?: string | null) {
  // 检查是否已存在相同请求
  if (requests.value.some(r => String(r.friendRequestId) === String(requestId))) {
    return
  }

  while (requests.value.length >= MAX_REQUESTS) {
    const oldest = requests.value.shift()
    if (oldest?.timerId) clearTimeout(oldest.timerId)
  }

  const id = `fr-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const timerId = window.setTimeout(() => dismiss(id), TOAST_DURATION)
  requests.value.push({
    id,
    friendRequestId: requestId,
    fromUserId,
    nickname,
    avatar,
    remark,
    timerId,
  })
}

function dismiss(id: string) {
  const idx = requests.value.findIndex(r => r.id === id)
  if (idx >= 0) {
    if (requests.value[idx].timerId) clearTimeout(requests.value[idx].timerId)
    requests.value.splice(idx, 1)
  }
}

async function accept(req: FriendRequestItem) {
  req.processing = true
  try {
    await friendApi.handleFriendRequest(req.friendRequestId, true)
    dismiss(req.id)
  } catch {
    req.processing = false
  }
}

async function reject(req: FriendRequestItem) {
  req.processing = true
  try {
    await friendApi.handleFriendRequest(req.friendRequestId, false)
    dismiss(req.id)
  } catch {
    req.processing = false
  }
}

function onAvatarError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

defineExpose({ pushFriendRequest })

onUnmounted(() => {
  for (const r of requests.value) {
    if (r.timerId) clearTimeout(r.timerId)
  }
  requests.value = []
})
</script>

<style scoped>
.friend-request-toasts {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 10001;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.req-toast {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 320px;
  max-width: 380px;
  padding: 14px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.18);
  pointer-events: auto;
  position: relative;
  animation: reqSlideIn 0.3s ease;
}
.req-toast:hover {
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.22);
}

.req-avatar {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  overflow: hidden;
  background: #e8eef5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.req-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.req-avatar-placeholder {
  font-size: 18px;
  font-weight: 600;
  color: #409eff;
}

.req-body {
  flex: 1;
  min-width: 0;
}
.req-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 2px;
}
.req-desc {
  font-size: 12px;
  color: #606266;
  margin-bottom: 2px;
}
.req-remark {
  font-size: 11px;
  color: #909399;
  font-style: italic;
}

.req-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
}
.req-btn {
  padding: 4px 14px;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
  min-width: 48px;
}
.req-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.req-btn.accept {
  background: #409eff;
  color: #fff;
}
.req-btn.accept:hover:not(:disabled) {
  background: #337ecc;
}
.req-btn.reject {
  background: #f0f0f0;
  color: #666;
}
.req-btn.reject:hover:not(:disabled) {
  background: #e0e0e0;
}

.req-close {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  font-size: 16px;
  color: #ccc;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}
.req-close:hover {
  background: #f0f0f0;
  color: #999;
}

@keyframes reqSlideIn {
  from { transform: translateX(120%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}

.req-toast-leave-active {
  animation: reqSlideOut 0.25s ease;
}
@keyframes reqSlideOut {
  from { transform: translateX(0); opacity: 1; }
  to { transform: translateX(120%); opacity: 0; }
}
</style>
