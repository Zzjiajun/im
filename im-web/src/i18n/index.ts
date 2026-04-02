import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN.json'
import en from '@/locales/en.json'

export type LocaleTag = 'zh-CN' | 'en'

const saved = (localStorage.getItem('im-locale') as LocaleTag | null) || 'zh-CN'

export const i18n = createI18n({
  legacy: false,
  locale: saved === 'en' ? 'en' : 'zh-CN',
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    en,
  },
})

export function setLocale(tag: LocaleTag) {
  i18n.global.locale.value = tag === 'en' ? 'en' : 'zh-CN'
  localStorage.setItem('im-locale', tag)
}
