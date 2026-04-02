import { http, unwrap } from './http'
import type {
  AssignFriendTagsRequest,
  CreateFriendTagRequest,
  FriendRequest,
  FriendTagVO,
  SnowflakeId,
  UpdateRemarkRequest,
  UserSimpleVO,
} from '@/types/api'

export function listFriends(tagId?: SnowflakeId) {
  return unwrap<UserSimpleVO[]>(
    http.get('/friends/list', { params: tagId != null ? { tagId } : {} })
  )
}

export function listFriendRequests() {
  return unwrap<FriendRequest[]>(http.get('/friends/requests'))
}

export function sendFriendRequest(toUserId: SnowflakeId, remark?: string) {
  return unwrap<void>(http.post('/friends/request', { toUserId, remark }))
}

export function handleFriendRequest(requestId: SnowflakeId, accept: boolean) {
  return unwrap<void>(http.post('/friends/handle', { requestId, accept }))
}

export function deleteFriend(friendUserId: SnowflakeId) {
  return unwrap<void>(http.delete(`/friends/${friendUserId}`))
}

export function updateFriendRemark(friendUserId: SnowflakeId, body: UpdateRemarkRequest) {
  return unwrap<void>(http.post(`/friends/${friendUserId}/remark`, body))
}

export function createFriendTag(body: CreateFriendTagRequest) {
  return unwrap<FriendTagVO>(http.post('/friends/tags', body))
}

export function listFriendTags() {
  return unwrap<FriendTagVO[]>(http.get('/friends/tags'))
}

export function deleteFriendTag(tagId: SnowflakeId) {
  return unwrap<void>(http.delete(`/friends/tags/${tagId}`))
}

export function assignFriendTags(body: AssignFriendTagsRequest) {
  return unwrap<void>(http.post('/friends/tags/assign', body))
}
