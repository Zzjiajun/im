import { defineStore } from "pinia";
import { ref, computed } from "vue";
import * as authApi from "@/api/auth";
import { getToken, setToken } from "@/utils/token";
import type { LoginRequest } from "@/types";

export const useAuthStore = defineStore("auth", () => {
  const nickname = ref<string | null>(null);
  const admin = ref(false);

  const isLoggedIn = computed(() => !!nickname.value && admin.value);

  async function login(payload: LoginRequest) {
    const res = await authApi.login(payload);
    if (Number(res.admin) !== 1) {
      setToken(null);
      throw new Error("非管理员账号，无法进入后台");
    }
    setToken(res.token);
    nickname.value = res.nickname;
    admin.value = true;
  }

  async function tryRestore() {
    if (!getToken()) return;
    try {
      const me = await authApi.fetchMe();
      if (Number(me.admin) !== 1) {
        logout();
        return;
      }
      nickname.value = me.nickname;
      admin.value = true;
    } catch {
      logout();
    }
  }

  function logout() {
    setToken(null);
    nickname.value = null;
    admin.value = false;
  }

  return { nickname, admin, isLoggedIn, login, logout, tryRestore };
});
