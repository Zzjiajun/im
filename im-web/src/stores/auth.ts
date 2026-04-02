import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import { getToken, setToken } from '@/utils/token'
import type { LoginRequest, LoginResponse, RegisterRequest, User } from '@/types/api'

const REFRESH_KEY = 'im_refresh'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const refreshToken = ref<string | null>(
    typeof localStorage !== 'undefined' ? localStorage.getItem(REFRESH_KEY) : null
  )
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  const isAdmin = computed(() => user.value?.admin === 1)

  function applyLoginResponse(res: LoginResponse) {
    token.value = res.token
    setToken(res.token)
    if (res.refreshToken) {
      refreshToken.value = res.refreshToken
      localStorage.setItem(REFRESH_KEY, res.refreshToken)
    }
    user.value = {
      id: res.userId,
      nickname: res.nickname,
      admin: res.admin ?? undefined,
    }
  }

  async function login(payload: LoginRequest) {
    const res = await authApi.login(payload)
    applyLoginResponse(res)
    await refreshProfile()
  }

  async function register(payload: RegisterRequest) {
    const res = await authApi.register(payload)
    applyLoginResponse(res)
    await refreshProfile()
  }

  /** 第三方登录等：写入 Token 并拉取完整资料 */
  async function applyOAuthLogin(res: LoginResponse) {
    applyLoginResponse(res)
    await refreshProfile()
  }

  async function refreshProfile() {
    try {
      user.value = await authApi.fetchMe()
    } catch {
      /* ignore */
    }
  }

  /** HTTP 拦截器 refresh 后同步 Pinia 与 WS */
  function syncTokenFromStorage() {
    token.value = getToken()
    refreshToken.value =
      typeof localStorage !== 'undefined' ? localStorage.getItem(REFRESH_KEY) : null
  }

  async function logoutRemote() {
    try {
      await authApi.logout({
        refreshToken: refreshToken.value || undefined,
      })
    } catch {
      /* 仍清理本地 */
    }
  }

  async function logout() {
    await logoutRemote()
    token.value = null
    refreshToken.value = null
    user.value = null
    setToken(null)
    localStorage.removeItem(REFRESH_KEY)
  }

  return {
    token,
    refreshToken,
    user,
    isLoggedIn,
    isAdmin,
    login,
    register,
    refreshProfile,
    syncTokenFromStorage,
    logout,
    logoutRemote,
    applyOAuthLogin,
  }
})
