<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { setLocale, type LocaleTag } from '@/i18n'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const account = ref('')
const password = ref('')
const err = ref('')
const busy = ref(false)

function onLang(e: Event) {
  const v = (e.target as HTMLSelectElement).value as LocaleTag
  setLocale(v)
}

async function submit() {
  err.value = ''
  busy.value = true
  try {
    await auth.login({
      authType: 'EMAIL',
      account: account.value.trim(),
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
}
</script>

<template>
  <div class="page">
    <div class="card">
      <header class="head">
        <h1>{{ t('app.title') }}</h1>
        <p class="sub">{{ t('auth.loginSubtitle') }}</p>
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
