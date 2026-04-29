<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import * as api from "@/api/adminApi";
import type { AdminUserRowVO, SnowflakeId } from "@/types";

const keyword = ref("");
const page = ref(1);
const size = 20;
const total = ref(0);
const records = ref<AdminUserRowVO[]>([]);
const loading = ref(false);
const err = ref("");

async function load() {
  loading.value = true;
  err.value = "";
  try {
    const res = await api.fetchUsers(page.value, size, keyword.value.trim() || undefined);
    total.value = res.total;
    records.value = res.records;
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
}

onMounted(load);

async function ban(id: SnowflakeId) {
  try {
    await ElMessageBox.confirm(`封禁用户 #${id}？将踢下线。`, "确认", {
      type: "warning",
      confirmButtonText: "封禁",
      cancelButtonText: "取消",
    });
    await api.banUser(id);
    ElMessage.success("已封禁");
    await load();
  } catch (e: unknown) {
    if (e === "cancel") return;
    ElMessage.error(e instanceof Error ? e.message : String(e));
  }
}

async function unban(id: SnowflakeId) {
  try {
    await ElMessageBox.confirm(`解除封禁 #${id}？`, "确认", {
      type: "info",
      confirmButtonText: "解封",
      cancelButtonText: "取消",
    });
    await api.unbanUser(id);
    ElMessage.success("已解封");
    await load();
  } catch (e: unknown) {
    if (e === "cancel") return;
    ElMessage.error(e instanceof Error ? e.message : String(e));
  }
}

function search() {
  page.value = 1;
  void load();
}

function onPageChange(p: number) {
  page.value = p;
  void load();
}
</script>

<template>
  <div>
    <h2 class="page-title">用户</h2>
    <el-space wrap class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="昵称或用户 ID"
        style="width: 280px"
        @keyup.enter="search"
      />
      <el-button type="primary" @click="search">搜索</el-button>
    </el-space>
    <el-alert v-if="err" type="error" :title="err" show-icon class="mb" :closable="false" />
    <el-table v-loading="loading" :data="records" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column label="手机" min-width="120">
        <template #default="{ row }">{{ row.phoneMasked || "—" }}</template>
      </el-table-column>
      <el-table-column label="邮箱" min-width="160">
        <template #default="{ row }">{{ row.emailMasked || "—" }}</template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">{{ row.status === 1 ? "正常" : "禁用" }}</template>
      </el-table-column>
      <el-table-column label="角色" width="100">
        <template #default="{ row }">{{ row.admin === 1 ? "管理员" : "用户" }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1 && row.admin !== 1"
            type="danger"
            link
            @click="ban(row.id)"
          >
            封禁
          </el-button>
          <el-button v-if="row.status != null && row.status !== 1" type="primary" link @click="unban(row.id)">
            解封
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > size"
      class="pager"
      background
      layout="prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="onPageChange"
    />
    <el-text v-else class="muted" size="small">共 {{ total }} 条</el-text>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
}
.toolbar {
  margin-bottom: 16px;
}
.mb {
  margin-bottom: 12px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.muted {
  margin-top: 12px;
  display: block;
  color: #909399;
}
</style>
