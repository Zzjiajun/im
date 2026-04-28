<script setup lang="ts">
import { ref, onMounted } from "vue";
import * as api from "@/api/adminApi";
import type { MessageReportAdminVO } from "@/types";

const list = ref<MessageReportAdminVO[]>([]);
const err = ref("");
const loading = ref(true);

onMounted(async () => {
  try {
    list.value = await api.fetchReports(150);
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div>
    <h2 class="page-title">举报</h2>
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-alert v-else-if="err" type="error" :title="err" show-icon :closable="false" />
    <el-empty v-else-if="!list.length" description="暂无举报" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="r in list"
        :key="r.id"
        :timestamp="r.createdAt || ''"
        placement="top"
      >
        <el-card shadow="hover">
          <template #header>
            <span class="head">#{{ r.id }}</span>
          </template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="举报人">
              {{ r.reporterNickname }} (#{{ r.reporterUserId }})
            </el-descriptions-item>
            <el-descriptions-item label="原因">{{ r.reason }}</el-descriptions-item>
            <el-descriptions-item v-if="r.remark" label="备注">{{ r.remark }}</el-descriptions-item>
            <el-descriptions-item label="消息预览">{{ r.messagePreview }}</el-descriptions-item>
            <el-descriptions-item label="关联">
              消息 #{{ r.messageId }} · 会话 #{{ r.conversationId }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
}
.head {
  font-weight: 600;
}
</style>
