<script setup lang="ts">
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const account = ref("");
const password = ref("");
const busy = ref(false);
const err = ref("");

async function submit() {
  err.value = "";
  if (!account.value.trim() || !password.value) {
    err.value = "请输入账号与密码";
    return;
  }
  busy.value = true;
  try {
    await auth.login({
      authType: "EMAIL",
      account: account.value.trim(),
      password: password.value,
    });
    const redir = (route.query.redirect as string) || "/";
    await router.replace(redir);
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="wrap">
    <el-card class="card" shadow="always">
      <h1>IM 管理后台</h1>
      <el-text size="small" type="info" class="hint">
        使用管理员账号登录（需在数据库中将 im_user.admin 设为 1）
      </el-text>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="邮箱">
          <el-input v-model="account" type="email" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="err" :title="err" type="error" show-icon :closable="false" class="mb" />
        <el-button type="primary" native-type="submit" :loading="busy" class="submit" style="width: 100%">
          {{ busy ? "登录中…" : "登录" }}
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.card {
  width: 100%;
  max-width: 400px;
}
h1 {
  margin: 0 0 8px;
  font-size: 1.35rem;
}
.hint {
  display: block;
  margin-bottom: 20px;
  line-height: 1.5;
}
.mb {
  margin-bottom: 12px;
}
.submit {
  margin-top: 8px;
}
</style>
