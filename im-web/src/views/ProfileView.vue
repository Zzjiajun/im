<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'
import * as uploadApi from '@/api/upload'
import type { AuthType, SnowflakeId, UserSessionVO } from '@/types/api'

const presetAvatarUrls = computed(() => {
  const b = import.meta.env.BASE_URL || '/'
  const base = b.endsWith('/') ? b : `${b}/`
  return Array.from({ length: 8 }, (_, i) => `${base}avatars/preset-${i + 1}.svg`)
})

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const msg = ref('')
const nickname = ref('')
const avatar = ref('')
const sessions = ref<UserSessionVO[]>([])

const codeAuthType = ref<AuthType>('PHONE')
const codeAccount = ref('')
const codePurpose = ref<'REGISTER' | 'RESET_PASSWORD'>('RESET_PASSWORD')

const resetAuthType = ref<AuthType>('PHONE')
const resetAccount = ref('')
const resetCode = ref('')
const resetPwd = ref('')

const oauthProvider = ref('stub')
const oauthOpenId = ref('')
const oauthNick = ref('')

const avatarFileInput = ref<HTMLInputElement | null>(null)

function pickPresetAvatar(url: string) {
  avatar.value = url
}

function onAvatarImgError(e: Event) {
  const el = e.target as HTMLImageElement
  el.style.display = 'none'
}

async function onAvatarFileChange(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  el.value = ''
  if (!f || !f.type.startsWith('image/')) {
    toast(t('profile.avatarFileOnly'))
    return
  }
  loading.value = true
  try {
    const media = await uploadApi.uploadMedia(f, 'IMAGE')
    if (media.url) {
      avatar.value = media.url
      toast(t('profile.avatarUploaded'))
    }
  } catch (err: unknown) {
    toast(err instanceof Error ? err.message : String(err))
  } finally {
    loading.value = false
  }
}

function toast(m: string) {
  msg.value = m
  setTimeout(() => {
    msg.value = ''
  }, 3000)
}

onMounted(async () => {
  loading.value = true
  try {
    await auth.refreshProfile()
    nickname.value = auth.user?.nickname || ''
    avatar.value = auth.user?.avatar || ''
    sessions.value = await authApi.fetchSessions()
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
})

async function saveProfile() {
  loading.value = true
  try {
    const u = await authApi.updateProfile({
      nickname: nickname.value || undefined,
      avatar: avatar.value || undefined,
    })
    auth.user = u
    toast(t('profile.saved'))
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  } finally {
    loading.value = false
  }
}

async function loadSessions() {
  try {
    sessions.value = await authApi.fetchSessions()
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  }
}

async function revoke(sid: SnowflakeId) {
  try {
    await authApi.revokeSession(sid)
    await loadSessions()
    toast(t('profile.sessionRevoked'))
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  }
}

async function logoutAll() {
  try {
    await authApi.logoutAll()
    toast(t('profile.logoutAllDone'))
    await loadSessions()
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  }
}

async function sendCode() {
  try {
    await authApi.sendVerifyCode({
      authType: 'EMAIL',
      account: codeAccount.value.trim(),
      purpose: codePurpose.value,
    })
    toast(t('profile.codeSent'))
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  }
}

async function doResetPassword() {
  try {
    await authApi.resetPassword({
      authType: 'EMAIL',
      account: resetAccount.value.trim(),
      verifyCode: resetCode.value.trim(),
      newPassword: resetPwd.value,
    })
    toast(t('profile.resetOk'))
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  }
}

async function oauthStub() {
  try {
    const res = await authApi.oauthLogin({
      provider: oauthProvider.value.trim(),
      openId: oauthOpenId.value.trim(),
      nickname: oauthNick.value.trim() || undefined,
    })
    await auth.applyOAuthLogin(res)
    toast(t('profile.oauthOk'))
    router.push({ name: 'chat' })
  } catch (e: unknown) {
    toast(e instanceof Error ? e.message : String(e))
  }
}

async function logoutHere() {
  await auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="page">
    <header class="head">
      <button type="button" class="back" @click="router.push('/')">← {{ t('common.back') }}</button>
      <h1>{{ t('profile.title') }}</h1>
      <nav class="quick-links">
        <RouterLink to="/favorites" class="ql">{{ t('profile.favoritesLink') }}</RouterLink>
        <RouterLink to="/groups" class="ql">{{ t('groups.title') }}</RouterLink>
      </nav>
    </header>

    <div v-if="loading && !auth.user" class="hint">{{ t('common.loading') }}</div>
    <div v-else class="body">
      <section class="card">
        <h2>{{ t('profile.me') }}</h2>
        <p class="id">ID: {{ auth.user?.id }}</p>
        <div class="avatar-block">
          <div class="avatar-preview-wrap">
            <img
              v-if="avatar"
              :src="avatar"
              alt=""
              class="avatar-preview"
              @error="onAvatarImgError"
            />
            <div v-else class="avatar-placeholder">{{ (nickname || '?').trim().slice(0, 1).toUpperCase() }}</div>
          </div>
          <div class="avatar-actions">
            <input
              ref="avatarFileInput"
              type="file"
              accept="image/*"
              class="sr"
              @change="onAvatarFileChange"
            />
            <button type="button" class="wx-btn-primary slim" @click="avatarFileInput?.click()">
              {{ t('profile.uploadAvatar') }}
            </button>
          </div>
        </div>
        <p class="preset-label">{{ t('profile.presetAvatars') }}</p>
        <div class="preset-row">
          <button
            v-for="(u, i) in presetAvatarUrls"
            :key="i"
            type="button"
            class="preset-btn"
            :class="{ on: avatar === u }"
            @click="pickPresetAvatar(u)"
          >
            <img :src="u" alt="" />
          </button>
        </div>
        <label class="field">
          <span>{{ t('auth.nickname') }}</span>
          <input v-model="nickname" class="wx-input" />
        </label>
        <label class="field">
          <span>{{ t('profile.avatarUrl') }}</span>
          <input v-model="avatar" class="wx-input" :placeholder="t('profile.avatarUrlHint')" />
        </label>
        <p class="sub-hint">{{ t('profile.avatarSaveHint') }}</p>
        <button type="button" class="wx-btn-primary" :disabled="loading" @click="saveProfile">
          {{ t('profile.save') }}
        </button>
      </section>

      <section class="card">
        <h2>{{ t('profile.sessions') }}</h2>
        <button type="button" class="linkish" @click="loadSessions">{{ t('common.retry') }}</button>
        <ul class="sess">
          <li v-for="s in sessions" :key="s.sessionId" class="sess-row">
            <div>
              <div>{{ s.deviceName || s.deviceId || '—' }}</div>
              <div class="sub">{{ s.lastActiveAt || s.createdAt }}</div>
            </div>
            <button
              v-if="!s.revoked"
              type="button"
              class="btn-sm"
              @click="revoke(s.sessionId)"
            >
              {{ t('profile.revoke') }}
            </button>
          </li>
        </ul>
        <button type="button" class="btn-warn" @click="logoutAll">{{ t('profile.logoutAll') }}</button>
      </section>

      <section class="card">
        <h2>{{ t('profile.sendCode') }}</h2>
        <p class="sub-hint">{{ t('auth.emailOnlyMode') }}</p>
        <input
          v-model="codeAccount"
          type="email"
          class="wx-input"
          :placeholder="t('auth.emailPlaceholder')"
        />
        <select v-model="codePurpose" class="wx-input">
          <option value="REGISTER">REGISTER</option>
          <option value="RESET_PASSWORD">RESET_PASSWORD</option>
        </select>
        <button type="button" class="wx-btn-primary" @click="sendCode">{{ t('profile.sendCodeBtn') }}</button>
      </section>

      <section class="card">
        <h2>{{ t('profile.resetPassword') }}</h2>
        <p class="sub-hint">{{ t('auth.emailOnlyMode') }}</p>
        <input
          v-model="resetAccount"
          type="email"
          class="wx-input"
          :placeholder="t('auth.emailPlaceholder')"
        />
        <input v-model="resetCode" class="wx-input" :placeholder="t('profile.verifyCode')" />
        <input v-model="resetPwd" type="password" class="wx-input" :placeholder="t('auth.password')" />
        <button type="button" class="wx-btn-primary" @click="doResetPassword">
          {{ t('profile.resetPassword') }}
        </button>
      </section>

      <section class="card">
        <h2>{{ t('profile.oauthStub') }}</h2>
        <input v-model="oauthProvider" class="wx-input" placeholder="provider" />
        <input v-model="oauthOpenId" class="wx-input" placeholder="openId" />
        <input v-model="oauthNick" class="wx-input" :placeholder="t('auth.nickname')" />
        <button type="button" class="wx-btn-primary" @click="oauthStub">{{ t('profile.oauthLogin') }}</button>
      </section>

      <button type="button" class="btn-warn full" @click="logoutHere">{{ t('auth.logout') }}</button>
    </div>

    <div v-if="msg" class="toast">{{ msg }}</div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: var(--wx-bg);
  padding: 16px;
  max-width: 560px;
  margin: 0 auto;
}
.head {
  margin-bottom: 16px;
}
.head h1 {
  font-size: 1.25rem;
  margin: 8px 0 0;
}
.quick-links {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.ql {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--wx-green);
}
.back {
  color: var(--wx-green);
  font-weight: 600;
}
.body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.card {
  background: var(--wx-white);
  border: 1px solid var(--wx-border);
  border-radius: 10px;
  padding: 14px;
}
.card h2 {
  font-size: 1rem;
  margin: 0 0 12px;
}
.sub-hint {
  font-size: 0.78rem;
  color: var(--wx-sub);
  margin: -6px 0 10px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 0.85rem;
}
.field span {
  color: var(--wx-sub);
}
.wx-input {
  margin-bottom: 8px;
}
.id {
  font-size: 0.85rem;
  color: var(--wx-sub);
  margin-bottom: 10px;
}
.sess {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
}
.sess-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #eee;
}
.sub {
  font-size: 0.75rem;
  color: var(--wx-sub);
}
.btn-sm {
  font-size: 0.75rem;
  padding: 4px 10px;
  border-radius: 4px;
  border: 1px solid var(--wx-border);
}
.linkish {
  font-size: 0.85rem;
  color: var(--wx-green);
  margin-bottom: 8px;
}
.btn-warn {
  margin-top: 8px;
  padding: 8px 14px;
  border-radius: 6px;
  background: #fff3e0;
  color: #e65100;
  border: 1px solid #ffcc80;
}
.btn-warn.full {
  width: 100%;
}
.hint {
  text-align: center;
  color: var(--wx-sub);
}
.toast {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
  z-index: 100;
}
.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}
.avatar-block {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 12px;
}
.avatar-preview-wrap {
  width: 72px;
  height: 72px;
  border-radius: 12px;
  overflow: hidden;
  background: #e8f5e9;
  flex-shrink: 0;
}
.avatar-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--wx-green, #07c160);
}
.avatar-actions {
  flex: 1;
  padding-top: 8px;
}
.slim {
  font-size: 0.85rem;
  padding: 8px 14px;
}
.preset-label {
  font-size: 0.82rem;
  color: var(--wx-sub);
  margin: 0 0 8px;
}
.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.preset-btn {
  width: 44px;
  height: 44px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  background: #f5f5f5;
}
.preset-btn.on {
  border-color: var(--wx-green, #07c160);
}
.preset-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
