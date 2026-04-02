<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { PublicAuthConfig } from '@/types/api'
import * as authApi from '@/api/auth'
import { setLocale, type LocaleTag } from '@/i18n'

const { t, locale } = useI18n()
const router = useRouter()

const publicCfg = ref<PublicAuthConfig | null>(null)

const deliveryHint = computed(() => {
  const p = publicCfg.value
  if (!p) return ''
  if (!p.emailDeliveryAvailable) return t('auth.emailUnavailable')
  return ''
})
const account = ref('')
const verifyCode = ref('')
const newPassword = ref('')
const err = ref('')
const busy = ref(false)
const codeBusy = ref(false)
const codeSec = ref(0)
let codeTimer: ReturnType<typeof setInterval> | null = null

const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function onLang(e: Event) {
  const v = (e.target as HTMLSelectElement).value as LocaleTag
  setLocale(v)
}

onMounted(async () => {
  try {
    publicCfg.value = await authApi.fetchPublicConfig()
  } catch {
    publicCfg.value = null
  }
})

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

async function sendCode() {
  err.value = ''
  const acc = account.value.trim()
  if (!acc) {
    err.value = t('auth.accountRequired')
    return
  }
  if (!emailRe.test(acc)) {
    err.value = t('auth.invalidEmail')
    return
  }
  if (codeSec.value > 0 || codeBusy.value) return
  codeBusy.value = true
  try {
    await authApi.sendVerifyCode({
      authType: 'EMAIL',
      account: acc,
      purpose: 'RESET_PASSWORD',
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
  if (!acc || !verifyCode.value.trim() || !newPassword.value) {
    err.value = t('common.error')
    return
  }
  if (!emailRe.test(acc)) {
    err.value = t('auth.invalidEmail')
    return
  }
  if (newPassword.value.length < 6) {
    err.value = t('auth.passwordMin')
    return
  }
  busy.value = true
  try {
    await authApi.resetPassword({
      authType: 'EMAIL',
      account: acc,
      verifyCode: verifyCode.value.trim(),
      newPassword: newPassword.value,
    })
    await router.replace('/login')
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="card">
      <header class="head">
        <h1>{{ t('auth.resetPasswordTitle') }}</h1>
        <p class="mode-note">{{ t('auth.emailOnlyMode') }}</p>
        <select
          class="lang"
          :value="locale === 'en' ? 'en' : 'zh-CN'"
          @change="onLang"
        >
          <option value="zh-CN">{{ t('common.zh') }}</option>
          <option value="en">{{ t('common.en') }}</option>
        </select>
      </header>

      <p v-if="deliveryHint" class="delivery-hint">{{ deliveryHint }}</p>

      <form class="form" @submit.prevent="submit">
        <label>
          <span>{{ t('auth.account') }}</span>
          <input
            v-model="account"
            type="email"
            inputmode="email"
            class="wx-input"
            required
            autocomplete="username"
            :placeholder="t('auth.emailPlaceholder')"
          />
        </label>
        <div class="code-row">
          <label class="grow">
            <span>{{ t('profile.verifyCode') }}</span>
            <input v-model="verifyCode" class="wx-input" required autocomplete="one-time-code" />
          </label>
          <button type="button" class="code-btn" :disabled="codeBusy || codeSec > 0" @click="sendCode">
            {{
              codeSec > 0 ? t('auth.waitSeconds', { n: codeSec }) : t('profile.sendCodeBtn')
            }}
          </button>
        </div>
        <label>
          <span>{{ t('auth.newPassword') }}</span>
          <input
            v-model="newPassword"
            type="password"
            class="wx-input"
            required
            minlength="6"
            autocomplete="new-password"
          />
        </label>
        <p class="hint">{{ t('auth.passwordMin') }}</p>
        <p v-if="err" class="err">{{ err }}</p>
        <button type="submit" class="wx-btn-primary" :disabled="busy">
          {{ busy ? t('common.loading') : t('profile.resetPassword') }}
        </button>
      </form>

      <p class="foot">
        <RouterLink to="/login">{{ t('auth.hasAccount') }}</RouterLink>
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
  margin: 0;
  font-size: 1.25rem;
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
.hint {
  margin: -6px 0 0;
  font-size: 0.75rem;
  color: var(--wx-sub);
}
.err {
  color: var(--wx-danger);
  font-size: 0.85rem;
  margin: 0;
}
.foot {
  text-align: center;
  margin-top: 18px;
}
</style>
