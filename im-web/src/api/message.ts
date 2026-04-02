import { http, unwrap } from './http'
import type {
  BatchFavoriteRequest,
  BatchForwardMessagesRequest,
  ChatMessageVO,
  MessageSearchPageVO,
  DeleteMessagesForSelfRequest,
  EditMessageRequest,
  FavoriteMessageRequest,
  FavoriteMessageVO,
  ForwardMessageRequest,
  MarkDeliveredRequest,
  MarkReadRequest,
  MergeForwardMessagesRequest,
  MessageReceiptVO,
  PinMessageRequest,
  ReactMessageRequest,
  RecallMessageRequest,
  ReportMessageRequest,
  SendMessageRequest,
  SnowflakeId,
} from '@/types/api'

export function sendMessage(body: SendMessageRequest) {
  return unwrap<ChatMessageVO>(http.post('/messages/send', body))
}

export function fetchMessages(
  conversationId: SnowflakeId,
  opts?: { beforeMessageId?: SnowflakeId; afterMessageId?: SnowflakeId; size?: number }
) {
  return unwrap<ChatMessageVO[]>(
    http.get(`/messages/conversation/${conversationId}`, { params: opts })
  )
}

export function searchMessages(
  keyword: string,
  opts?: {
    conversationId?: SnowflakeId
    beforeMessageId?: SnowflakeId
    size?: number
  }
) {
  return unwrap<MessageSearchPageVO>(
    http.get('/messages/search', {
      params: {
        keyword,
        ...(opts?.conversationId != null ? { conversationId: opts.conversationId } : {}),
        ...(opts?.beforeMessageId != null ? { beforeMessageId: opts.beforeMessageId } : {}),
        ...(opts?.size != null ? { size: opts.size } : {}),
      },
    })
  )
}

export function favoriteMessage(body: FavoriteMessageRequest) {
  return unwrap<void>(http.post('/messages/favorite', body))
}

export function batchFavoriteMessages(body: BatchFavoriteRequest) {
  return unwrap<void>(http.post('/messages/favorite/batch', body))
}

export function cancelFavorite(messageId: SnowflakeId) {
  return unwrap<void>(http.delete(`/messages/favorite/${messageId}`))
}

export function updateFavorite(body: FavoriteMessageRequest) {
  return unwrap<void>(http.post('/messages/favorite/update', body))
}

export function forwardMessages(body: ForwardMessageRequest) {
  return unwrap<ChatMessageVO[]>(http.post('/messages/forward', body))
}

export function batchForwardMessages(body: BatchForwardMessagesRequest) {
  return unwrap<ChatMessageVO[]>(http.post('/messages/forward/batch', body))
}

export function mergeForwardMessages(body: MergeForwardMessagesRequest) {
  return unwrap<ChatMessageVO[]>(http.post('/messages/forward/merge', body))
}

export function markDelivered(body: MarkDeliveredRequest) {
  return unwrap<void>(http.post('/messages/deliver', body))
}

export function deleteMessagesForSelf(body: DeleteMessagesForSelfRequest) {
  return unwrap<void>(http.post('/messages/delete-self', body))
}

export function listFavorites(keyword?: string, categoryName?: string) {
  return unwrap<FavoriteMessageVO[]>(
    http.get('/messages/favorites', {
      params: {
        ...(keyword ? { keyword } : {}),
        ...(categoryName ? { categoryName } : {}),
      },
    })
  )
}

export function pinMessage(body: PinMessageRequest) {
  return unwrap<void>(http.post('/messages/pin', body))
}

export function unpinMessage(body: PinMessageRequest) {
  return unwrap<void>(http.post('/messages/unpin', body))
}

export function listPinnedMessages(conversationId?: SnowflakeId) {
  return unwrap<ChatMessageVO[]>(
    http.get('/messages/pinned', {
      params: conversationId != null ? { conversationId } : {},
    })
  )
}

export function listMessageReads(messageId: SnowflakeId) {
  return unwrap<MessageReceiptVO[]>(http.get(`/messages/${messageId}/reads`))
}

export function listMessageDelivers(messageId: SnowflakeId) {
  return unwrap<MessageReceiptVO[]>(http.get(`/messages/${messageId}/delivers`))
}

export function markMessagesRead(body: MarkReadRequest) {
  return unwrap<void>(http.post('/messages/read', body))
}

export function recallMessage(body: RecallMessageRequest) {
  return unwrap<void>(http.post('/messages/recall', body))
}

export function editMessage(body: EditMessageRequest) {
  return unwrap<ChatMessageVO>(http.post('/messages/edit', body))
}

export function reportMessage(body: ReportMessageRequest) {
  return unwrap<void>(http.post('/messages/report', body))
}

export function reactMessage(body: ReactMessageRequest) {
  return unwrap<void>(http.post('/messages/react', body))
}

export function removeReaction(body: ReactMessageRequest) {
  return unwrap<void>(http.post('/messages/react/remove', body))
}
