<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import type { PublicAuthConfig, AuthType } from '@/types/api'
import * as authApi from '@/api/auth'
import { setLocale, type LocaleTag } from '@/i18n'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const publicCfg = ref<PublicAuthConfig | null>(null)
const authType = ref<AuthType>('EMAIL')

const account = ref('')
const password = ref('')
const verifyCode = ref('')
const err = ref('')
const busy = ref(false)
const codeBusy = ref(false)
const codeSec = ref(0)
let codeTimer: ReturnType<typeof setInterval> | null = null

const phoneAuthEnabled = computed(() => publicCfg.value?.phoneAuthEnabled === true)
const smsStub = computed(() => publicCfg.value?.smsStubMode === true)

const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const phoneRe = /^1\d{10}$/

function onLang(e: Event) {
  const v = (e.target as HTMLSelectElement).value as LocaleTag
  setLocale(v)
}

function startCodeCooldown() {
  codeSec.value = 60
  if (codeTimer) clearInterval(codeTimer)
  codeTimer = setInterval(() => {
    codeSec.value -= 1
    if (codeSec.value <= 0 && codeTimer) {
      clearInterval(codeTimer)
      codeTimer = null
    }
  }, 1000)
}

onMounted(async () => {
  try {
    publicCfg.value = await authApi.fetchPublicConfig()
  } catch {
    publicCfg.value = null
  }
})

function validate(): boolean {
  const acc = account.value.trim()
  if (!acc) {
    err.value = t('auth.accountRequired')
    return false
  }
  if (authType.value === 'EMAIL') {
    if (!emailRe.test(acc)) {
      err.value = t('auth.invalidEmail')
      return false
    }
  } else {
    if (!phoneRe.test(acc)) {
      err.value = t('auth.invalidPhone')
      return false
    }
  }
  return true
}

async function sendCode() {
  err.value = ''
  if (!validate()) return
  if (codeSec.value > 0 || codeBusy.value) return
  codeBusy.value = true
  try {
    await authApi.sendVerifyCode({
      authType: authType.value,
      account: account.value.trim(),
      purpose: 'LOGIN',
    })
    startCodeCooldown()
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    codeBusy.value = false
  }
}

async function submit() {
  err.value = ''
  const acc = account.value.trim()
  if (!acc) { err.value = t('auth.accountRequired'); return }
  if (authType.value === 'EMAIL') {
    if (!emailRe.test(acc)) { err.value = t('auth.invalidEmail'); return }
    if (!password.value) { err.value = t('common.error'); return }
    busy.value = true
    try {
      await auth.login({
        authType: 'EMAIL',
        account: acc,
        password: password.value,
        deviceId: 'im-web',
        deviceName: 'Browser',
      })
      const redirect = (route.query.redirect as string) || '/'
      await router.replace(redirect)
    } catch (e: unknown) {
      err.value = e instanceof Error ? e.message : String(e)
    } finally {
      busy.value = false
    }
  } else {
    if (!phoneRe.test(acc)) { err.value = t('auth.invalidPhone'); return }
    if (!verifyCode.value.trim()) { err.value = t('auth.verifyRequiredErr'); return }
    busy.value = true
    try {
      await auth.loginByVerifyCode({
        authType: 'PHONE',
        account: acc,
        verifyCode: verifyCode.value.trim(),
        deviceId: 'im-web',
        deviceName: 'Browser',
      })
      const redirect = (route.query.redirect as string) || '/'
      await router.replace(redirect)
    } catch (e: unknown) {
      err.value = e instanceof Error ? e.message : String(e)
    } finally {
      busy.value = false
    }
  }
}
</script>

<template>
  <div class="page">
    <div class="card">
      <header class="head">
        <h1>{{ t('app.title') }}</h1>
        <p class="sub">{{ t('auth.loginSubtitle') }}</p>
        <p v-if="!phoneAuthEnabled" class="mode-note">{{ t('auth.emailOnlyMode') }}</p>
        <select
          class="lang"
          :value="locale === 'en' ? 'en' : 'zh-CN'"
          @change="onLang"
        >
          <option value="zh-CN">{{ t('common.zh') }}</option>
          <option value="en">{{ t('common.en') }}</option>
        </select>
      </header>

      <div v-if="phoneAuthEnabled" class="tabs">
        <button
          :class="['tab', authType === 'EMAIL' ? 'active' : '']"
          @click="authType = 'EMAIL'"
        >
          {{ t('auth.emailLogin') }}
        </button>
        <button
          :class="['tab', authType === 'PHONE' ? 'active' : '']"
          @click="authType = 'PHONE'"
        >
          {{ t('auth.phoneLogin') }}
        </button>
      </div>

      <p v-if="smsStub && authType === 'PHONE'" class="delivery-hint">
        {{ t('auth.smsStub') }}
      </p>

      <form class="form" @submit.prevent="submit">
        <label>
          <span>{{ t('auth.account') }}</span>
          <input
            v-model="account"
            :type="authType === 'EMAIL' ? 'email' : 'tel'"
            :inputmode="authType === 'EMAIL' ? 'email' : 'numeric'"
            class="wx-input"
            required
            autocomplete="username"
            :placeholder="authType === 'EMAIL' ? t('auth.emailPlaceholder') : t('auth.phonePlaceholder')"
          />
        </label>

        <template v-if="authType === 'EMAIL'">
          <label>
            <span>{{ t('auth.password') }}</span>
            <input
              v-model="password"
              type="password"
              class="wx-input"
              required
              autocomplete="current-password"
            />
          </label>
        </template>

        <template v-else>
          <div class="code-row">
            <label class="grow">
              <span>{{ t('profile.verifyCode') }}</span>
              <input
                v-model="verifyCode"
                class="wx-input"
                required
                autocomplete="one-time-code"
                :placeholder="t('profile.verifyCode')"
              />
            </label>
            <button type="button" class="code-btn" :disabled="codeBusy || codeSec > 0" @click="sendCode">
              {{ codeSec > 0 ? t('auth.waitSeconds', { n: codeSec }) : t('profile.sendCodeBtn') }}
            </button>
          </div>
        </template>

        <p v-if="err" class="err">{{ err }}</p>
        <button type="submit" class="wx-btn-primary" :disabled="busy">
          {{ busy ? t('common.loading') : t('auth.login') }}
        </button>
      </form>

      <p class="foot">
        <RouterLink to="/forgot-password">{{ t('auth.forgotPassword') }}</RouterLink>
        ·
        <RouterLink to="/register">{{ t('auth.noAccount') }}</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--auth-page-bg);
}
.card {
  width: 100%;
  max-width: 420px;
  background: var(--wx-white);
  border-radius: 16px;
  padding: 28px 24px 20px;
  box-shadow: var(--wx-card-shadow);
  border: 1px solid rgba(0, 0, 0, 0.04);
}
.mode-note {
  margin: 8px 0 0;
  font-size: 0.78rem;
  color: var(--wx-sub);
}
.delivery-hint {
  margin: 0 0 14px;
  padding: 10px 12px;
  font-size: 0.78rem;
  line-height: 1.45;
  color: #856404;
  background: #fff8e6;
  border-radius: 10px;
  border: 1px solid #ffe082;
}
.head {
  text-align: center;
  position: relative;
  margin-bottom: 20px;
}
.head h1 {
  margin: 0 0 6px;
  font-size: 1.35rem;
}
.sub {
  margin: 0;
  color: var(--wx-sub);
  font-size: 0.8rem;
  padding: 0 28px;
}
.lang {
  position: absolute;
  right: 0;
  top: 0;
  font-size: 0.8rem;
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid var(--wx-border);
}
.tabs {
  display: flex;
  gap: 0;
  margin-bottom: 16px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--wx-border);
}
.tab {
  flex: 1;
  padding: 10px;
  border: none;
  background: #f5f5f5;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.2s;
}
.tab.active {
  background: var(--wx-green, #07c160);
  color: #fff;
  font-weight: 600;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.form label span {
  display: block;
  font-size: 0.8rem;
  margin-bottom: 4px;
  color: var(--wx-sub);
}
.code-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.grow {
  flex: 1;
  min-width: 0;
}
.code-btn {
  flex-shrink: 0;
  padding: 10px 12px;
  white-space: nowrap;
  border-radius: 8px;
  border: 1px solid var(--wx-border);
  background: #f5f5f5;
  cursor: pointer;
  font-size: 0.85rem;
}
.code-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.err {
  color: var(--wx-danger);
  font-size: 0.85rem;
  margin: 0;
}
.foot {
  text-align: center;
  margin-top: 18px;
  font-size: 0.85rem;
}
.foot a {
  color: var(--wx-green);
}
</style>
