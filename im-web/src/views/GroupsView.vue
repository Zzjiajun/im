<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import * as convApi from '@/api/conversation'
import type { ConversationListVO, SnowflakeId } from '@/types/api'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const err = ref('')
const groups = ref<ConversationListVO[]>([])

function initial(name?: string | null) {
  const s = (name || '?').trim()
  return s.slice(0, 1).toUpperCase()
}

function openGroup(id: SnowflakeId) {
  router.push({ path: '/', query: { openConv: String(id) } })
}

async function load() {
  loading.value = true
  err.value = ''
  try {
    groups.value = await convApi.fetchMyGroups()
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
    groups.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <header class="head">
      <button type="button" class="back" @click="router.push('/')">← {{ t('common.back') }}</button>
      <h1>{{ t('groups.title') }}</h1>
      <p class="sub">{{ t('groups.subtitle') }}</p>
      <button type="button" class="mini" @click="load">{{ t('common.retry') }}</button>
    </header>

    <div v-if="loading" class="hint">{{ t('common.loading') }}</div>
    <div v-else-if="err" class="err">{{ err }}</div>
    <ul v-else class="list">
      <li v-if="!groups.length" class="muted">{{ t('groups.empty') }}</li>
      <li v-for="g in groups" :key="String(g.conversationId)" class="row" @click="openGroup(g.conversationId)">
        <div class="avatar">{{ initial(g.displayName) }}</div>
        <div class="meta">
          <div class="name">{{ g.displayName }}</div>
          <div class="preview">{{ g.lastMessagePreview || '—' }}</div>
        </div>
        <span v-if="g.memberCount != null" class="cnt">{{ g.memberCount }}{{ t('groups.membersUnit') }}</span>
        <span class="go">{{ t('groups.open') }} →</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: var(--wx-bg);
  padding: 16px;
  max-width: 560px;
  margin: 0 auto;
}
.head h1 {
  font-size: 1.2rem;
  margin: 8px 0 4px;
}
.sub {
  font-size: 0.82rem;
  color: var(--wx-sub);
  margin: 0 0 10px;
}
.back {
  color: var(--wx-green);
  font-weight: 600;
}
.mini {
  font-size: 0.8rem;
  padding: 4px 12px;
  margin-bottom: 8px;
}
.hint,
.err {
  text-align: center;
  padding: 24px;
  color: var(--wx-sub);
}
.err {
  color: #c62828;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 10px;
  background: var(--wx-white);
  border: 1px solid var(--wx-border);
  border-radius: 10px;
  margin-bottom: 10px;
  cursor: pointer;
}
.row:active {
  opacity: 0.92;
}
.avatar {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  background: #e8f5e9;
  color: var(--wx-green);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}
.meta {
  flex: 1;
  min-width: 0;
}
.name {
  font-weight: 600;
  font-size: 0.95rem;
}
.preview {
  font-size: 0.78rem;
  color: var(--wx-sub);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cnt {
  font-size: 0.72rem;
  color: var(--wx-sub);
  flex-shrink: 0;
}
.go {
  font-size: 0.78rem;
  color: var(--wx-green);
  flex-shrink: 0;
}
.muted {
  padding: 24px;
  text-align: center;
  color: var(--wx-sub);
}
</style>
