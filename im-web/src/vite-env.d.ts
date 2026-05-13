/// <reference types="vite/client" />

export {}

declare module 'vue-router' {
  interface RouteMeta {
    /** 需通过地区/语言落地校验（登录、注册、忘记密码等访客入口） */
    landingGate?: boolean
  }
}

interface ImportMetaEnv {
  readonly VITE_API_BASE: string
  readonly VITE_WS_URL: string
  /** 设为 true 时跳过落地页地区/语言校验（本地联调用） */
  readonly VITE_LANDING_GATE_DISABLED?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
