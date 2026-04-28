<template>
  <div v-if="typingUsers.length > 0" class="typing-indicator">
    <div class="typing-dots">
      <span class="dot"></span>
      <span class="dot"></span>
      <span class="dot"></span>
    </div>
    <div class="typing-text">
      <template v-if="typingUsers.length === 1">
        {{ typingUsers[0].nickname }} 正在输入...
      </template>
      <template v-else>
        {{ typingUsers.map(u => u.nickname).join('、') }} 正在输入...
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAuthStore } from '@/stores/auth'
import { stompTyping } from '@/composables/useStomp'
import type { SnowflakeId } from '@/types/api'

const chat = useChatStore()
const auth = useAuthStore()

// 正在输入的用户列表 [{userId, nickname, timestamp}]
const typingUsers = ref<Array<{ userId: SnowflakeId; nickname: string; timestamp: number }>>([])

const TYPING_TIMEOUT = 3000 // 3秒后清除输入状态

// 清理过期的输入状态
function cleanupExpiredTyping() {
  const now = Date.now()
  typingUsers.value = typingUsers.value.filter(t => now - t.timestamp < TYPING_TIMEOUT)
}

// 定期清理过期状态
let cleanupTimer: number | null = null

function startCleanupTimer() {
  if (cleanupTimer) return
  cleanupTimer = window.setInterval(() => {
    cleanupExpiredTyping()
    if (typingUsers.value.length === 0) {
      stopCleanupTimer()
    }
  }, 1000)
}

function stopCleanupTimer() {
  if (cleanupTimer) {
    clearInterval(cleanupTimer)
    cleanupTimer = null
  }
}

// 处理正在输入事件
function handleTypingEvent(event: Event) {
  const customEvent = event as CustomEvent<{
    conversationId: SnowflakeId
    userId: SnowflakeId
    typing: boolean
  }>

  const { conversationId, userId, typing } = customEvent.detail

  // 只处理当前会话的输入事件
  if (!conversationId || conversationId !== chat.activeId) {
    return
  }

  // 忽略自己的输入状态
  if (userId === auth.user?.id) {
    return
  }

  if (typing) {
    // 查找是否已经在列表中
    const existingIndex = typingUsers.value.findIndex(t => t.userId === userId)

    if (existingIndex >= 0) {
      // 更新时间戳
      typingUsers.value[existingIndex].timestamp = Date.now()
    } else {
      // 获取用户昵称
      const member = chat.activeConversation?.members?.find((m: any) => m.userId === userId)
      const nickname = member?.nickname || `用户${userId}`

      typingUsers.value.push({
        userId,
        nickname,
        timestamp: Date.now()
      })

      // 开始清理定时器
      startCleanupTimer()
    }
  } else {
    // 停止输入，移除该用户
    typingUsers.value = typingUsers.value.filter(t => t.userId !== userId)
  }
}

// 监听输入事件
onMounted(() => {
  window.addEventListener('im-typing', handleTypingEvent)
})

onUnmounted(() => {
  window.removeEventListener('im-typing', handleTypingEvent)
  stopCleanupTimer()
})
</script>

<style scoped>
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 8px;
  font-size: 12px;
  color: #666;
  margin: 8px 0;
}

.typing-dots {
  display: flex;
  gap: 3px;
}

.dot {
  width: 6px;
  height: 6px;
  background: #409eff;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.typing-text {
  font-weight: 500;
}
</style>
