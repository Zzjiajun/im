<script setup lang="ts">
import { ref } from "vue";
import * as api from "@/api/adminApi";
import type { ChatMessageVO, SnowflakeId } from "@/types";

const keyword = ref("");
const convId = ref("");
const items = ref<ChatMessageVO[]>([]);
const hasMore = ref(false);
const nextBefore = ref<SnowflakeId | null>(null);
const err = ref("");
const loading = ref(false);

async function search(reset: boolean) {
  if (!keyword.value.trim()) return;
  err.value = "";
  loading.value = true;
  try {
    const page = await api.adminSearchMessages(keyword.value.trim(), {
      conversationId: convId.value.trim() || undefined,
      beforeMessageId: reset ? undefined : nextBefore.value || undefined,
      size: 50,
    });
    if (reset) {
      items.value = page.items;
    } else {
      items.value.push(...page.items);
    }
    hasMore.value = page.hasMore;
    nextBefore.value = page.nextBeforeMessageId;
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div>
    <h2 class="page-title">全库消息搜索</h2>
    <el-text size="small" type="info" class="hint">
      空格分词为 AND；仅排除已撤回消息。可选填会话 ID 缩小范围。
    </el-text>
    <el-space wrap class="row">
      <el-input
        v-model="keyword"
        clearable
        placeholder="关键词"
        style="width: 240px"
        @keyup.enter="search(true)"
      />
      <el-input v-model="convId" clearable placeholder="会话 ID（可选）" style="width: 200px" />
      <el-button type="primary" :loading="loading" @click="search(true)">搜索</el-button>
    </el-space>
    <el-alert v-if="err" type="error" :title="err" show-icon class="mb" :closable="false" />
    <el-table v-loading="loading" :data="items" stripe border style="width: 100%">
      <el-table-column prop="id" label="消息 ID" width="120" />
      <el-table-column prop="conversationId" label="会话" width="120" />
      <el-table-column label="发送者" min-width="120">
        <template #default="{ row }">{{ row.senderNickname || row.senderId }}</template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column label="内容" min-width="220">
        <template #default="{ row }">{{ row.content?.slice(0, 200) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>
    <el-button
      v-if="hasMore"
      class="more"
      :loading="loading"
      @click="search(false)"
    >
      加载更多
    </el-button>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}
.hint {
  display: block;
  margin-bottom: 16px;
}
.row {
  margin-bottom: 12px;
}
.mb {
  margin-bottom: 12px;
}
.more {
  margin-top: 16px;
}
</style>
