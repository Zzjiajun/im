export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 后端 Long（雪花 ID 等）在 JSON 中为 string，避免 JS 精度丢失 */
export type SnowflakeId = string

/** GET /api/auth/public-config */
export interface PublicAuthConfig {
  verifyOnRegister: boolean
  emailDeliveryAvailable: boolean
  smsStubMode: boolean
  /** 后端为 true 时才展示手机号登录/注册 */
  phoneAuthEnabled?: boolean
}

export type AuthType = 'PHONE' | 'EMAIL'

export interface LoginRequest {
  authType: AuthType
  account: string
  password: string
  deviceId?: string
  deviceName?: string
}

export interface RegisterRequest {
  authType: AuthType
  account: string
  password: string
  nickname: string
  verifyCode?: string
}

export interface LoginResponse {
  userId: SnowflakeId
  nickname: string
  token: string
  refreshToken?: string
  /** 1 表示管理员 */
  admin?: number | null
}

export interface User {
  id: SnowflakeId
  nickname: string
  avatar?: string | null
  phone?: string | null
  email?: string | null
  admin?: number | null
  status?: number | null
}

export interface UserSearchVO {
  userId: SnowflakeId
  nickname: string
  avatar?: string | null
  phone?: string | null
  email?: string | null
  friend?: boolean
}

export interface UserSimpleVO {
  userId: SnowflakeId
  nickname: string
  aliasName?: string | null
  avatar?: string | null
  phone?: string | null
  email?: string | null
  tagIds?: SnowflakeId[]
}

export interface FriendRequest {
  id: SnowflakeId
  fromUserId: SnowflakeId
  toUserId: SnowflakeId
  remark?: string | null
  status: string
  createdAt?: string | null
}

export interface ConversationListVO {
  conversationId: SnowflakeId
  type: string
  displayName: string
  displayAvatar?: string | null
  remarkName?: string | null
  notice?: string | null
  ownerId?: SnowflakeId | null
  lastMessagePreview?: string | null
  lastMessageId?: SnowflakeId | null
  unreadCount?: number
  pinned?: boolean
  muted?: boolean
  archived?: boolean
  targetUserId?: SnowflakeId | null
  updatedAt?: string | null
  memberCount?: number
  draftContent?: string | null
  draftUpdatedAt?: string | null
}

export interface MessageReplyVO {
  messageId?: SnowflakeId
  senderId?: SnowflakeId
  senderNickname?: string | null
  type?: string | null
  content?: string | null
  mediaUrl?: string | null
}

export interface MessageReactionSummaryVO {
  reactionType?: string
  count?: number
  reactedByMe?: boolean
}

/** 后端消息里媒体附件元信息（JSON 字段） */
export interface MessageMediaMetaVO {
  mediaType?: string | null
  originalName?: string | null
  contentType?: string | null
  size?: string | number | null
}

export interface ChatMessageVO {
  id: SnowflakeId
  conversationId: SnowflakeId
  senderId: SnowflakeId
  senderNickname?: string | null
  senderAvatar?: string | null
  type: string
  content?: string | null
  mediaUrl?: string | null
  mediaCoverUrl?: string | null
  mediaMeta?: MessageMediaMetaVO | null
  createdAt?: string | null
  recalled?: number
  edited?: number
  editedAt?: string | null
  replyMessageId?: SnowflakeId | null
  replyMessage?: MessageReplyVO | null
  mentionAll?: boolean
  mentionUserIds?: SnowflakeId[]
  readCount?: number
  deliveredCount?: number
  pinnedByMe?: boolean
  reactions?: MessageReactionSummaryVO[] | null
  /** 客户端幂等键，重试时与请求一致 */
  clientMsgId?: string | null
}

export interface MessageSearchPageVO {
  items: ChatMessageVO[]
  hasMore: boolean
  nextBeforeMessageId: SnowflakeId | null
}

export interface SendMessageRequest {
  conversationId: SnowflakeId
  type: string
  /** 可选；网络重试时复用同一值可去重 */
  clientMsgId?: string
  content?: string
  mediaUrl?: string
  mediaCoverUrl?: string
  replyMessageId?: SnowflakeId
  mentionAll?: boolean
  mentionUserIds?: SnowflakeId[]
}

export interface WsEnvelope<T = unknown> {
  event: string
  data: T
}

export interface RecallWsPayload {
  conversationId: SnowflakeId
  messageId: SnowflakeId
  operatorId?: SnowflakeId
}

export interface GroupMemberVO {
  userId: SnowflakeId
  nickname?: string | null
  avatar?: string | null
  phone?: string | null
  email?: string | null
  role?: string | null
  online?: boolean
  blockedByMe?: boolean
  hasBlockedMe?: boolean
}

export interface GroupDetailVO {
  conversationId: SnowflakeId
  name?: string | null
  avatar?: string | null
  remarkName?: string | null
  notice?: string | null
  ownerId?: SnowflakeId | null
  muteAll?: boolean
  memberCount?: number
  members?: GroupMemberVO[]
  noticeUpdatedAt?: string | null
  updatedAt?: string | null
}

export interface MediaFile {
  id: SnowflakeId
  url: string
  mediaType?: string | null
  originalName?: string | null
  contentType?: string | null
}

export type VerifyCodePurpose = 'REGISTER' | 'RESET_PASSWORD'

export interface SendVerifyCodeRequest {
  authType: AuthType
  account: string
  purpose: VerifyCodePurpose
}

export interface ResetPasswordRequest {
  authType: AuthType
  account: string
  verifyCode: string
  newPassword: string
}

export interface RefreshTokenRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken?: string
}

export interface OAuthLoginRequest {
  provider: string
  openId: string
  nickname?: string
  deviceId?: string
  deviceName?: string
}

export interface UpdateProfileRequest {
  nickname?: string
  avatar?: string
}

export interface UserSessionVO {
  sessionId: SnowflakeId
  deviceId?: string | null
  deviceName?: string | null
  createdAt?: string | null
  lastActiveAt?: string | null
  revoked?: boolean
}

export interface UserOnlineStatusVO {
  userId: SnowflakeId
  online?: boolean
}

export interface RegisterPushTokenRequest {
  platform: string
  deviceToken: string
}

export interface FriendTagVO {
  tagId: SnowflakeId
  name?: string | null
  sortOrder?: number
  memberCount?: number
  createdAt?: string | null
}

export interface CreateFriendTagRequest {
  name: string
  sortOrder?: number
}

export interface AssignFriendTagsRequest {
  friendUserId: SnowflakeId
  tagIds: SnowflakeId[]
}

export interface ConversationUnreadVO {
  conversationId: SnowflakeId
  unreadCount: number
}

export interface UpdateConversationSettingsRequest {
  pinned?: boolean
  muted?: boolean
  archived?: boolean
}

export interface UpdateRemarkRequest {
  remark?: string
}

export interface UpdateDraftRequest {
  draftContent?: string
}

export interface ClearConversationRequest {
  beforeMessageId?: SnowflakeId
}

export interface UpdateSyncCursorRequest {
  messageId: SnowflakeId
}

export interface CreateGroupRequest {
  name: string
  avatar?: string
  memberIds: SnowflakeId[]
}

export interface GroupMemberOperateRequest {
  memberIds: SnowflakeId[]
}

export interface TransferOwnerRequest {
  targetUserId: SnowflakeId
}

export interface UpdateGroupProfileRequest {
  name?: string
  avatar?: string
  notice?: string
}

export interface GroupMuteAllRequest {
  muteAll: boolean
}

export interface MuteGroupMemberRequest {
  userId: SnowflakeId
  mutedUntil?: string | null
}

export interface CreateGroupInviteRequest {
  expireHours?: number
  maxUses?: number
}

export interface GroupInviteCreatedVO {
  token: string
  expireAt?: string | null
  maxUses?: number | null
}

export interface StartVoiceCallRequest {
  conversationId: SnowflakeId
}

export interface VoiceCallVO {
  callId: string
  conversationId: SnowflakeId
  channelName: string
  callerUserId: SnowflakeId
  callerNickname?: string | null
  callerAvatar?: string | null
  calleeUserId: SnowflakeId
  calleeNickname?: string | null
  calleeAvatar?: string | null
  status: string
  reason?: string | null
  createdAt?: string | null
  answeredAt?: string | null
  endedAt?: string | null
}

export interface AgoraRtcTokenVO {
  appId: string
  channelName: string
  uid: string
  token: string
  expiresInSeconds: number
}

export interface JoinGroupInviteRequest {
  token: string
}

export interface MarkReadRequest {
  conversationId: SnowflakeId
  lastReadMessageId?: SnowflakeId
}

export interface MarkDeliveredRequest {
  messageIds: SnowflakeId[]
}

export interface RecallMessageRequest {
  messageId: SnowflakeId
}

export interface EditMessageRequest {
  messageId: SnowflakeId
  content: string
  mentionAll?: boolean
  mentionUserIds?: SnowflakeId[]
}

export interface ReportMessageRequest {
  messageId: SnowflakeId
  reason: string
  remark?: string
}

export interface ReactMessageRequest {
  messageId: SnowflakeId
  reactionType: string
}

export interface PinMessageRequest {
  messageId: SnowflakeId
}

export interface ForwardMessageRequest {
  sourceMessageId: SnowflakeId
  targetConversationIds: SnowflakeId[]
}

export interface BatchForwardMessagesRequest {
  sourceMessageIds: SnowflakeId[]
  targetConversationIds: SnowflakeId[]
}

export interface MergeForwardMessagesRequest {
  sourceMessageIds: SnowflakeId[]
  targetConversationIds: SnowflakeId[]
  title: string
}

export interface FavoriteMessageRequest {
  messageId: SnowflakeId
  note?: string
  categoryName?: string
}

export interface BatchFavoriteRequest {
  messageIds: SnowflakeId[]
  note?: string
  categoryName?: string
}

export interface DeleteMessagesForSelfRequest {
  messageIds: SnowflakeId[]
}

export interface FavoriteMessageVO {
  favoriteId: SnowflakeId
  messageId: SnowflakeId
  note?: string | null
  categoryName?: string | null
  favoriteAt?: string | null
  message?: ChatMessageVO
}

export interface MessageReceiptVO {
  userId: SnowflakeId
  nickname?: string | null
  avatar?: string | null
  actionAt?: string | null
}

export interface StickerItemVO {
  itemId: SnowflakeId
  code?: string | null
  imageUrl?: string | null
  sortOrder?: number
}

export interface StickerPackDetailVO {
  packId: SnowflakeId
  code?: string | null
  name?: string | null
  coverUrl?: string | null
  sortOrder?: number
  items?: StickerItemVO[]
}

export interface CreateStickerPackRequest {
  code: string
  name: string
  coverUrl?: string
  sortOrder?: number
}

export interface CreateStickerItemRequest {
  packId: SnowflakeId
  code: string
  imageUrl: string
  sortOrder?: number
}

export interface MessageReportAdminVO {
  id: SnowflakeId
  messageId: SnowflakeId
  reporterUserId: SnowflakeId
  reporterNickname?: string | null
  reason?: string | null
  remark?: string | null
  createdAt?: string | null
  messagePreview?: string | null
  conversationId?: SnowflakeId | null
}
