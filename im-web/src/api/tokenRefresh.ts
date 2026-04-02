import axios from 'axios'
import type { ApiResponse, LoginResponse } from '@/types/api'
import { setToken } from '@/utils/token'

const baseURL = import.meta.env.VITE_API_BASE || '/api'

const raw = axios.create({ baseURL, timeout: 30000 })

/** 无鉴权拦截器，仅用于 refresh，避免循环依赖 */
export async function refreshAccessToken(refreshToken: string): Promise<LoginResponse> {
  const { data } = await raw.post<ApiResponse<LoginResponse>>('/auth/refresh', {
    refreshToken,
  })
  if (data && typeof data.code === 'number' && data.code !== 0) {
    throw new Error(data.message || 'Refresh failed')
  }
  const lr = data.data
  setToken(lr.token)
  if (lr.refreshToken) {
    localStorage.setItem('im_refresh', lr.refreshToken)
  }
  window.dispatchEvent(new CustomEvent('im-token-refreshed'))
  return lr
}
