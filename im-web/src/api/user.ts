import { http, unwrap } from './http'
import type {
  RegisterPushTokenRequest,
  SnowflakeId,
  UserOnlineStatusVO,
  UserSearchVO,
  UserSimpleVO,
} from '@/types/api'

export function searchUsers(keyword: string) {
  return unwrap<UserSearchVO[]>(http.get('/users/search', { params: { keyword } }))
}

export function fetchOnlineStatus(userIds: SnowflakeId[]) {
  if (!userIds.length) return Promise.resolve([] as UserOnlineStatusVO[])
  const q = new URLSearchParams()
  for (const id of userIds) q.append('userIds', String(id))
  return unwrap<UserOnlineStatusVO[]>(http.get(`/users/online-status?${q.toString()}`))
}

export function addBlacklist(targetUserId: SnowflakeId) {
  return unwrap<void>(http.post(`/users/blacklist/${targetUserId}`))
}

export function removeBlacklist(targetUserId: SnowflakeId) {
  return unwrap<void>(http.delete(`/users/blacklist/${targetUserId}`))
}

export function listBlacklist() {
  return unwrap<UserSimpleVO[]>(http.get('/users/blacklist'))
}

export function registerPushToken(body: RegisterPushTokenRequest) {
  return unwrap<void>(http.post('/users/push-token', body))
}
