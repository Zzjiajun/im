<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
const code = String(route.query.code ?? "UNKNOWN");
</script>

<template>
  <div class="page">
    <div class="card">
      <header class="head">
        <h1>访问受限</h1>
      </header>
      <p>您当前无法访问此页面。</p>
      <p>错误代码：{{ code }}</p>
      <router-link class="button" :to="{ name: 'login' }">返回登录</router-link>
    </div>
  </div>
</template>
