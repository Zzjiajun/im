<script setup lang="ts">
import { ref, onMounted } from "vue";
import * as api from "@/api/adminApi";
import type { AdminDashboardVO } from "@/types";

const data = ref<AdminDashboardVO | null>(null);
const err = ref("");
const loading = ref(true);

onMounted(async () => {
  try {
    data.value = await api.fetchDashboard();
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div>
    <h2 class="page-title">概览</h2>
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-alert v-else-if="err" type="error" :title="err" show-icon :closable="false" />
    <el-row v-else-if="data" :gutter="16">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover">
          <el-statistic title="注册用户" :value="data.totalUsers" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover">
          <el-statistic title="近 24 小时消息" :value="data.messagesLast24h" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card shadow="hover">
          <el-statistic title="近 7 日举报" :value="data.reportsLast7d" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
}
</style>
