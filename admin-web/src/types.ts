export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export type SnowflakeId = string;

export interface LoginRequest {
  authType: "PHONE" | "EMAIL";
  account: string;
  password: string;
}

export interface LoginResponse {
  userId: SnowflakeId;
  nickname: string;
  token: string;
  refreshToken?: string;
  admin?: number | null;
}

export interface User {
  id: SnowflakeId;
  nickname: string;
  admin?: number | null;
}

export interface AdminDashboardVO {
  totalUsers: number;
  messagesLast24h: number;
  reportsLast7d: number;
}

export interface AdminUserRowVO {
  id: SnowflakeId;
  nickname: string;
  phoneMasked?: string | null;
  emailMasked?: string | null;
  status?: number | null;
  admin?: number | null;
  createdAt?: string | null;
}

export interface AdminUserPageVO {
  total: number;
  records: AdminUserRowVO[];
}

export interface MessageReportAdminVO {
  id: SnowflakeId;
  messageId: SnowflakeId;
  reporterUserId: SnowflakeId;
  reporterNickname: string;
  reason: string;
  remark?: string | null;
  createdAt?: string | null;
  messagePreview?: string | null;
  conversationId?: SnowflakeId | null;
}

export interface ChatMessageVO {
  id: SnowflakeId;
  conversationId: SnowflakeId;
  senderId: SnowflakeId;
  senderNickname?: string | null;
  type: string;
  content?: string | null;
  createdAt?: string | null;
}

export interface MessageSearchPageVO {
  items: ChatMessageVO[];
  hasMore: boolean;
  nextBeforeMessageId: SnowflakeId | null;
}

// 通知中心相关接口
export interface NotificationVO {
  id: number;
  type: string;
  title: string;
  content: string;
  data?: string | null;
  senderId?: SnowflakeId | null;
  senderNickname?: string | null;
  senderAvatar?: string | null;
  relatedId?: SnowflakeId | null;
  isRead: boolean;
  readAt?: string | null;
  createdAt?: string | null;
}

export interface NotificationUnreadVO {
  totalCount: number;
  unreadCount: number;
}

export interface NotificationListRequest {
  isRead?: boolean;
  page?: number;
  size?: number;
}
