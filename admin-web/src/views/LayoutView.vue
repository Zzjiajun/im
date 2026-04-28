<script setup lang="ts">
import { RouterView, useRoute, useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

function logout() {
  auth.logout();
  router.push({ name: "login" });
}
</script>

<template>
  <el-container class="layout-root">
    <el-aside width="220px" class="aside">
      <div class="brand">IM 管理后台</div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#001529"
        text-color="rgba(255,255,255,0.75)"
        active-text-color="#fff"
      >
        <el-menu-item index="/">概览</el-menu-item>
        <el-menu-item index="/users">用户</el-menu-item>
        <el-menu-item index="/reports">举报</el-menu-item>
        <el-menu-item index="/messages">消息搜索</el-menu-item>
        <el-menu-item index="/notifications">通知中心</el-menu-item>
      </el-menu>
      <div class="foot">
        <el-text size="small" class="who" truncated>{{ auth.nickname }}</el-text>
        <el-button size="small" plain @click="logout">退出</el-button>
      </div>
    </el-aside>
    <el-main class="main">
      <RouterView />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout-root {
  min-height: 100vh;
}
.aside {
  display: flex;
  flex-direction: column;
  background: #001529;
  color: #fff;
}
.brand {
  padding: 16px;
  font-weight: 700;
  font-size: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}
.aside :deep(.el-menu) {
  flex: 1;
  border-right: none;
}
.foot {
  padding: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.who {
  color: rgba(255, 255, 255, 0.85);
}
.main {
  background: #f0f2f5;
  padding: 24px;
}
</style>
