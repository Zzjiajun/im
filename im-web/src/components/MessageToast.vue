<template>
  <Teleport to="body">
    <div class="message-toast-container" v-if="visible">
      <TransitionGroup name="toast-slide">
        <div
          v-for="toast in toasts"
          :key="toast.id"
          class="message-toast"
          @click="handleClick(toast)"
        >
          <div class="toast-avatar">
            <img
              v-if="toast.senderAvatar"
              :src="toast.senderAvatar"
              alt=""
              @error="onAvatarError"
            />
            <span v-else class="toast-avatar-placeholder">
              {{ (toast.senderNickname || '?')[0] }}
            </span>
          </div>
          <div class="toast-body">
            <div class="toast-name">{{ toast.senderNickname || '未知用户' }}</div>
            <div class="toast-preview">{{ toastPreview(toast) }}</div>
          </div>
          <button class="toast-close" @click.stop="dismiss(toast.id)">×</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import type { ChatMessageVO, SnowflakeId } from '@/types/api'

interface ToastItem {
  id: string
  conversationId: SnowflakeId
  senderId: SnowflakeId
  senderNickname?: string
  senderAvatar?: string | null
  type: string
  content?: string | null
  mediaUrl?: string | null
  createdAt: string
  timerId?: number
}

const router = useRouter()
const chat = useChatStore()

const toasts = ref<ToastItem[]>([])
const visible = ref(true)

const TOAST_DURATION = 0 // 0 = 不自动消失，用户点击或关闭才消失（微信风格）
const MAX_TOASTS = 3 // 最多同时显示3条

/** 收到新消息时从外部调用 */
function pushMessage(vo: ChatMessageVO) {
  // 如果是当前活跃会话的消息，不弹窗
  if (chat.activeId && String(vo.conversationId) === String(chat.activeId)) {
    return
  }
  // 如果已存在相同会话的toast，更新时间戳位置
  const existing = toasts.value.find(t => String(t.conversationId) === String(vo.conversationId))
  if (existing) {
    if (existing.timerId) clearTimeout(existing.timerId)
    // 更新到最新消息预览
    existing.content = vo.content
    existing.type = vo.type
    existing.mediaUrl = vo.mediaUrl
    existing.createdAt = vo.createdAt || new Date().toISOString()
    // 如果启用自动消失，重置定时器
    if (TOAST_DURATION > 0) {
      existing.timerId = window.setTimeout(() => dismiss(existing.id), TOAST_DURATION)
    }
    return
  }

  // 限制最大数量
  while (toasts.value.length >= MAX_TOASTS) {
    const oldest = toasts.value.shift()
    if (oldest?.timerId) clearTimeout(oldest.timerId)
  }

  const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const timerId = TOAST_DURATION > 0 ? window.setTimeout(() => dismiss(id), TOAST_DURATION) : undefined
  toasts.value.push({
    id,
    conversationId: vo.conversationId,
    senderId: vo.senderId,
    senderNickname: vo.senderNickname,
    senderAvatar: vo.senderAvatar,
    type: vo.type,
    content: vo.content,
    mediaUrl: vo.mediaUrl,
    createdAt: vo.createdAt || new Date().toISOString(),
    timerId,
  })
}

function dismiss(id: string) {
  const idx = toasts.value.findIndex(t => t.id === id)
  if (idx >= 0) {
    if (toasts.value[idx].timerId) clearTimeout(toasts.value[idx].timerId)
    toasts.value.splice(idx, 1)
  }
}

function handleClick(toast: ToastItem) {
  // 点击toast跳转到对应会话
  dismiss(toast.id)
  router.push(`/?openConv=${toast.conversationId}`)
}

function toastPreview(toast: ToastItem): string {
  if (toast.type === 'IMAGE') return '[图片]'
  if (toast.type === 'VIDEO') return '[视频]'
  if (toast.type === 'VOICE') return '[语音]'
  if (toast.type === 'FILE') return '[文件]'
  if (toast.type === 'SYSTEM') return '[系统消息]'
  if (toast.content) return toast.content.slice(0, 60)
  return '[消息]'
}

function onAvatarError(e: Event) {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}

/** 暴露给外部的API */
defineExpose({ pushMessage })

onUnmounted(() => {
  // 清理所有定时器
  for (const t of toasts.value) {
    if (t.timerId) clearTimeout(t.timerId)
  }
  toasts.value = []
})
</script>

<style scoped>
.message-toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 10000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.message-toast {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 280px;
  max-width: 360px;
  padding: 12px 14px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.18);
  cursor: pointer;
  pointer-events: auto;
  transition: transform 0.3s ease, opacity 0.3s ease;
}
.message-toast:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.22);
}

.toast-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  background: #e8eef5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.toast-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.toast-avatar-placeholder {
  font-size: 16px;
  font-weight: 600;
  color: #409eff;
}

.toast-body {
  flex: 1;
  min-width: 0;
}
.toast-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.toast-preview {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toast-close {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border: none;
  background: #f0f0f0;
  border-radius: 50%;
  font-size: 14px;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}
.toast-close:hover {
  background: #e0e0e0;
  color: #666;
}

/* 动画 */
.toast-slide-enter-active {
  animation: slideIn 0.3s ease;
}
.toast-slide-leave-active {
  animation: slideOut 0.25s ease;
}
@keyframes slideIn {
  from { transform: translateX(120%); opacity: 0; }
  to { transform: translateX(0); opacity: 1; }
}
@keyframes slideOut {
  from { transform: translateX(0); opacity: 1; }
  to { transform: translateX(120%); opacity: 0; }
}
</style>
