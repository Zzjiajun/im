import { http, unwrap } from './http'
import type {
  ClearConversationRequest,
  ConversationListVO,
  ConversationUnreadVO,
  CreateGroupInviteRequest,
  CreateGroupRequest,
  GroupDetailVO,
  GroupInviteCreatedVO,
  GroupMemberOperateRequest,
  GroupMuteAllRequest,
  JoinGroupInviteRequest,
  MuteGroupMemberRequest,
  SnowflakeId,
  TransferOwnerRequest,
  UpdateConversationSettingsRequest,
  UpdateDraftRequest,
  UpdateGroupProfileRequest,
  UpdateRemarkRequest,
  UpdateSyncCursorRequest,
  GroupMemberVO,
} from '@/types/api'

export function fetchConversations() {
  return unwrap<ConversationListVO[]>(http.get('/conversations/list'))
}

/** 当前用户加入的群聊（不含单聊） */
export function fetchMyGroups() {
  return unwrap<ConversationListVO[]>(http.get('/conversations/groups'))
}

export function fetchArchivedConversations() {
  return unwrap<ConversationListVO[]>(http.get('/conversations/archived'))
}

export function fetchHiddenConversations() {
  return unwrap<ConversationListVO[]>(http.get('/conversations/hidden'))
}

export function restoreConversation(conversationId: SnowflakeId) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/restore`))
}

export function syncConversationCursor(conversationId: SnowflakeId, body: UpdateSyncCursorRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/sync-cursor`, body))
}

export function setGroupMuteAll(conversationId: SnowflakeId, body: GroupMuteAllRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/mute-all`, body))
}

export function muteGroupMember(conversationId: SnowflakeId, body: MuteGroupMemberRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/members/mute`, body))
}

export function createGroupInvite(conversationId: SnowflakeId, body?: CreateGroupInviteRequest) {
  return unwrap<GroupInviteCreatedVO>(
    http.post(`/conversations/${conversationId}/invite`, body ?? {})
  )
}

export function joinGroupByInvite(body: JoinGroupInviteRequest) {
  return unwrap<GroupDetailVO>(http.post('/conversations/join-invite', body))
}

export function fetchUnreadSummary() {
  return unwrap<ConversationUnreadVO[]>(http.get('/conversations/unread'))
}

export function markConversationRead(conversationId: SnowflakeId) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/read`))
}

export function addGroupMembers(conversationId: SnowflakeId, body: GroupMemberOperateRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/members/add`, body))
}

export function removeGroupMembers(conversationId: SnowflakeId, body: GroupMemberOperateRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/members/remove`, body))
}

export function leaveGroup(conversationId: SnowflakeId) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/leave`))
}

export function createSingleConversation(targetUserId: SnowflakeId) {
  return unwrap<ConversationListVO>(
    http.post(`/conversations/single/${targetUserId}`)
  )
}

export function createGroup(body: CreateGroupRequest) {
  return unwrap<GroupDetailVO>(http.post('/conversations/group', body))
}

export function fetchGroupDetail(conversationId: SnowflakeId) {
  return unwrap<GroupDetailVO>(
    http.get(`/conversations/${conversationId}/group-detail`)
  )
}

export function updateGroupProfile(conversationId: SnowflakeId, body: UpdateGroupProfileRequest) {
  return unwrap<GroupDetailVO>(
    http.post(`/conversations/${conversationId}/profile`, body)
  )
}

export function fetchGroupMembers(conversationId: SnowflakeId) {
  return unwrap<GroupMemberVO[]>(http.get(`/conversations/${conversationId}/members`))
}

export function hideConversation(conversationId: SnowflakeId) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/hide`))
}

export function updateConversationSettings(
  conversationId: SnowflakeId,
  body: UpdateConversationSettingsRequest
) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/settings`, body))
}

export function updateConversationRemark(conversationId: SnowflakeId, body: UpdateRemarkRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/remark`, body))
}

export function updateConversationDraft(conversationId: SnowflakeId, body: UpdateDraftRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/draft`, body))
}

export function clearConversationHistory(conversationId: SnowflakeId, body: ClearConversationRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/clear`, body))
}

export function addGroupAdmins(conversationId: SnowflakeId, body: GroupMemberOperateRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/admins/add`, body))
}

export function removeGroupAdmins(conversationId: SnowflakeId, body: GroupMemberOperateRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/admins/remove`, body))
}

export function transferGroupOwner(conversationId: SnowflakeId, body: TransferOwnerRequest) {
  return unwrap<void>(http.post(`/conversations/${conversationId}/owner/transfer`, body))
}
