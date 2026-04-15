import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { getToken, setToken } from '@/utils/token'
import type { ApiResponse } from '@/types/api'
import { refreshAccessToken } from './tokenRefresh'

const baseURL = import.meta.env.VITE_API_BASE || '/api'

export const http = axios.create({
  baseURL,
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function isAuthPath(url: string | undefined) {
  if (!url) return false
  return (
    url.includes('/auth/login') ||
    url.includes('/auth/register') ||
    url.includes('/auth/refresh') ||
    url.includes('/auth/send-code') ||
    url.includes('/auth/reset-password') ||
    url.includes('/auth/oauth/login')
  )
}

http.interceptors.response.use(
  (res) => {
    const body = res.data as ApiResponse<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      const cfg = res.config
      console.warn('[im] API 业务错误', cfg?.method, cfg?.url, body.message)
      return Promise.reject(new Error(body.message || 'Request failed'))
    }
    return res
  },
  async (err: AxiosError<ApiResponse<unknown>>) => {
    const status = err.response?.status
    const cfg = err.config as (InternalAxiosRequestConfig & { _imRetry?: boolean }) | undefined
    if (
      cfg &&
      !cfg._imRetry &&
      (status === 401 || status === 403) &&
      !isAuthPath(cfg.url) &&getToken()
    ) {
      const rt = localStorage.getItem('im_refresh')
      if (rt) {
        try {
          cfg._imRetry = true
          await refreshAccessToken(rt)
          cfg.headers = cfg.headers || {}
          cfg.headers.Authorization = `Bearer ${getToken()}`
          return http(cfg)
        } catch {
          setToken(null)
          localStorage.removeItem('im_refresh')
          window.dispatchEvent(new CustomEvent('im-auth-failed'))
        }
      }
    }
    const msg =
      err.response?.data?.message || err.message || 'Network error'
    if (cfg?.url) {
      console.warn('[im] API 请求失败', cfg.method, cfg.url, err.response?.status, msg)
    }
    return Promise.reject(new Error(msg))
  }
)

export async function unwrap<T>(p: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data } = await p
  return data.data as T
}
