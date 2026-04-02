<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import * as adminApi from '@/api/admin'
import type { MessageReportAdminVO } from '@/types/api'

const { t } = useI18n()
const router = useRouter()
const list = ref<MessageReportAdminVO[]>([])
const loading = ref(false)
const err = ref('')

onMounted(async () => {
  loading.value = true
  try {
    list.value = await adminApi.listReports(100)
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <header class="head">
      <button type="button" class="back" @click="router.push('/tools')">← {{ t('common.back') }}</button>
      <h1>{{ t('admin.title') }}</h1>
    </header>
    <p v-if="loading" class="muted">{{ t('common.loading') }}</p>
    <p v-else-if="err" class="err">{{ err }}</p>
    <ul v-else class="list">
      <li v-for="r in list" :key="r.id" class="item">
        <div class="row">
          <span class="id">#{{ r.id }}</span>
          <span class="muted">{{ r.createdAt }}</span>
        </div>
        <div>{{ t('admin.reporter') }}: {{ r.reporterNickname }} (#{{ r.reporterUserId }})</div>
        <div>{{ t('admin.reason') }}: {{ r.reason }}</div>
        <div v-if="r.remark">{{ r.remark }}</div>
        <div class="preview">{{ r.messagePreview }}</div>
        <div class="muted">msg #{{ r.messageId }} / conv #{{ r.conversationId }}</div>
      </li>
      <li v-if="!list.length" class="muted">{{ t('tools.empty') }}</li>
    </ul>
  </div>
</template>

<style scoped>
.page {
  padding: 16px;
  max-width: 720px;
  margin: 0 auto;
  min-height: 100%;
  background: var(--wx-bg);
}
.back {
  color: var(--wx-green);
  font-weight: 600;
}
h1 {
  font-size: 1.2rem;
  margin: 8px 0 16px;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.item {
  background: var(--wx-white);
  border: 1px solid var(--wx-border);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  font-size: 0.88rem;
}
.row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.preview {
  margin-top: 6px;
  color: var(--wx-sub);
  word-break: break-all;
}
.muted {
  color: var(--wx-sub);
}
.err {
  color: #c62828;
}
</style>
