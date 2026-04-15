import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as convApi from '@/api/conversation'
import * as msgApi from '@/api/message'
import type { ChatMessageVO, ConversationListVO, SendMessageRequest, SnowflakeId } from '@/types/api'
import { stompDeliver } from '@/composables/useStomp'
import { sortSnowflakeIds } from '@/utils/ids'

/** 后端 Long 与 JSON 可能表现为 string 或 number，比较时统一为字符串 */
function idEq(a: SnowflakeId | null | undefined, b: SnowflakeId | null | undefined): boolean {
  if (a == null || b == null) return false
  return String(a) === String(b)
}

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<ConversationListVO[]>([])
  const activeId = ref<SnowflakeId | null>(null)
  const messages = ref<ChatMessageVO[]>([])
  const loadingList = ref(false)
  const loadingMsg = ref(false)
  const sending = ref(false)
  const beforeCursor = ref<SnowflakeId | undefined>(undefined)
  const selfId = ref<SnowflakeId | null>(null)

  /**
   * 对方先触发送达/已读（WS 先到）而本地尚未把发送接口返回的消息插入列表时，先记入此处，插入后再合并。
   * 否则会出现「要点一下会话重新拉消息才看到已送达」的现象。
   */
  const pendingDelivered = ref<Record<string, number>>({})
  const pendingRead = ref<Record<string, number>>({})

  /**
   * 消息列表在任意 await 期间仍可能被发送/WS 更新；拉取返回后用旧快照覆盖会「吃掉」新消息。
   * 若在请求过程中发生过本地列表变更，则与接口结果合并去重，而不是整表替换。
   */
  let messagesSyncGeneration = 0

  const activeConversation = computed(() =>
    conversations.value.find((c) => idEq(c.conversationId, activeId.value))
  )

  const conversationsSorted = computed(() => {
    const arr = [...conversations.value]
    arr.sort((a, b) => {
      const pa = a.pinned ? 1 : 0
      const pb = b.pinned ? 1 : 0
      if (pa !== pb) return pb - pa
      const ta = a.updatedAt ? new Date(a.updatedAt).getTime() : 0
      const tb = b.updatedAt ? new Date(b.updatedAt).getTime() : 0
      return tb - ta
    })
    return arr
  })

  function setSelfId(id: SnowflakeId | null) {
    selfId.value = id
  }

  async function loadConversations() {
    loadingList.value = true
    try {
      const raw = await convApi.fetchConversations()
      const list = Array.isArray(raw) ? raw : []
      const prev = conversations.value
      const byId = new Map<string, ConversationListVO>()
      for (const c of list) {
        if (c?.conversationId != null) {
          byId.set(String(c.conversationId), c)
        }
      }
      // 当前选中会话若本次接口未包含，保留本地条目（避免并发刷新/时序问题把列表冲空）
      const aid = activeId.value
      if (aid != null && !byId.has(String(aid))) {
        const keep = prev.find((x) => idEq(x.conversationId, aid))
        if (keep) {
          byId.set(String(aid), keep)
        }
      }
      conversations.value = [...byId.values()]
    } finally {
      loadingList.value = false
    }
  }

  async function syncReceiptsForList(list: ChatMessageVO[], conversationId: SnowflakeId) {
    const uid = selfId.value
    if (!uid || !list.length) return
    const toDeliver = list
      .filter((m) => !idEq(m.senderId, uid) && !m.recalled)
      .map((m) => m.id)
    if (toDeliver.length) {
      stompDeliver(toDeliver)
      try {
        await msgApi.markDelivered({ messageIds: toDeliver })
      } catch {
        /* 送达失败不阻塞 */
      }
    }
    const last = list[list.length - 1]
    if (last && idEq(activeId.value, conversationId)) {
      try {
        await msgApi.markMessagesRead({
          conversationId,
          lastReadMessageId: last.id,
        })
      } catch {
        /* 已读失败不阻塞 */
      }
    }
  }

  async function selectConversation(id: SnowflakeId) {
    const sid = String(id) as SnowflakeId
    activeId.value = sid
    beforeCursor.value = undefined
    messages.value = []
    messagesSyncGeneration++
    const c = conversations.value.find((x) => idEq(x.conversationId, sid))
    if (c && c.unreadCount) c.unreadCount = 0
    try {
      await convApi.markConversationRead(sid)
    } catch {
      /* 已读同步失败不阻塞拉消息 */
    }
    await loadMessages(sid, false)
  }

  async function loadMessages(conversationId: SnowflakeId, loadMore: boolean) {
    loadingMsg.value = true
    try {
      if (!loadMore) {
        pendingDelivered.value = {}
        pendingRead.value = {}
      }
      const genBeforeFetch = messagesSyncGeneration
      const list = await msgApi.fetchMessages(conversationId, {
        size: 40,
        beforeMessageId: loadMore ? beforeCursor.value : undefined,
      })
      if (!idEq(activeId.value, conversationId)) {
        return
      }
      if (loadMore && list.length) {
        messages.value = dedupeById([...list, ...messages.value])
      } else if (!loadMore) {
        messages.value =
          genBeforeFetch !== messagesSyncGeneration
            ? dedupeById([...list, ...messages.value])
            : list
      }
      if (list.length) {
        beforeCursor.value = list[0].id
      }
      if (!loadMore && list.length) {
        await syncReceiptsForList(messages.value, conversationId)
      }
    } finally {
      loadingMsg.value = false
    }
  }

  /** WebSocket 重连后：按最后一条消息 id 拉取增量，补全断线期间消息 */
  async function syncNewerMessages(conversationId: SnowflakeId) {
    if (!idEq(activeId.value, conversationId)) return
    if (!messages.value.length) {
      await loadMessages(conversationId, false)
      return
    }
    const lastId = messages.value[messages.value.length - 1]?.id
    if (lastId == null) return
    try {
      const newer = await msgApi.fetchMessages(conversationId, {
        afterMessageId: lastId,
        size: 100,
      })
      if (!idEq(activeId.value, conversationId)) return
      if (newer.length) {
        dropPendingForMessageIds(newer.map((m) => m.id))
        const current = messages.value
        messages.value = dedupeById([...current, ...newer])
        await syncReceiptsForList(messages.value, conversationId)
      }
    } catch {
      /* 增量失败不阻塞界面 */
    }
  }

  function dedupeById(arr: ChatMessageVO[]) {
    const m = new Map<SnowflakeId, ChatMessageVO>()
    for (const x of arr) m.set(x.id, x)
    const keys = sortSnowflakeIds([...m.keys()])
    return keys.map((id) => m.get(id)!)
  }

  function dropPendingForMessageIds(ids: SnowflakeId[]) {
    if (!ids.length) return
    let d = pendingDelivered.value
    let r = pendingRead.value
    for (const id of ids) {
      const k = String(id)
      if (k in d) {
        const { [k]: _, ...rest } = d
        d = rest
      }
      if (k in r) {
        const { [k]: __, ...rest } = r
        r = rest
      }
    }
    pendingDelivered.value = d
    pendingRead.value = r
  }

  function mergePendingReceipts(vo: ChatMessageVO): ChatMessageVO {
    const k = String(vo.id)
    const pd = pendingDelivered.value[k]
    const pr = pendingRead.value[k]
    if (pd == null && pr == null) return vo
    let d = vo.deliveredCount ?? 0
    let r = vo.readCount ?? 0
    if (pd != null) d += pd
    if (pr != null) r += pr
    const { [k]: _d, ...restD } = pendingDelivered.value
    const { [k]: _r, ...restR } = pendingRead.value
    pendingDelivered.value = restD
    pendingRead.value = restR
    return { ...vo, deliveredCount: d, readCount: r }
  }

  function upsertConversation(vo: ConversationListVO) {
    const i = conversations.value.findIndex((x) => idEq(x.conversationId, vo.conversationId))
    if (i >= 0) {
      conversations.value[i] = { ...conversations.value[i], ...vo }
    } else {
      conversations.value = [vo, ...conversations.value]
    }
  }

  function patchConversation(conversationId: SnowflakeId, patch: Partial<ConversationListVO>) {
    const i = conversations.value.findIndex((x) => idEq(x.conversationId, conversationId))
    if (i >= 0) {
      conversations.value[i] = { ...conversations.value[i], ...patch }
    }
  }

  function clearMessages() {
    messages.value = []
    messagesSyncGeneration++
  }

  function clearActive() {
    activeId.value = null
    messages.value = []
    beforeCursor.value = undefined
    pendingDelivered.value = {}
    pendingRead.value = {}
    messagesSyncGeneration++
  }

  async function sendMessage(body: SendMessageRequest) {
    if (!activeId.value || sending.value) return null
    sending.value = true
    try {
      const payload: SendMessageRequest = {
        ...body,
        clientMsgId: body.clientMsgId ?? crypto.randomUUID(),
      }
      const vo = await msgApi.sendMessage(payload)
      appendOrReplace(vo)
      const aid = activeId.value
      if (aid) {
        patchConversation(aid, {
          lastMessagePreview: previewForList(vo),
          updatedAt: vo.createdAt || new Date().toISOString(),
        })
      }
      return vo
    } finally {
      sending.value = false
    }
  }

  function previewForList(vo: ChatMessageVO): string {
    if (vo.type === 'IMAGE') return '[图片]'
    if (vo.type === 'VIDEO') return '[视频]'
    if (vo.type === 'FILE') return '[文件]'
    if (vo.type === 'VOICE') return '[语音]'
    if (vo.content) return vo.content.slice(0, 80)
    return '—'
  }

  async function sendImage(mediaUrl: string, extra?: Partial<SendMessageRequest>) {
    if (!activeId.value || !mediaUrl?.trim()) return null
    return sendMessage({
      conversationId: activeId.value,
      type: 'IMAGE',
      mediaUrl: mediaUrl.trim(),
      ...extra,
    })
  }

  async function sendText(content: string, extra?: Partial<SendMessageRequest>) {
    if (!activeId.value || !content.trim()) return null
    return sendMessage({
      conversationId: activeId.value,
      type: 'TEXT',
      content: content.trim(),
      ...extra,
    })
  }

  async function sendMediaMessage(
    type: string,
    mediaUrl: string,
    extra?: Partial<SendMessageRequest>
  ) {
    if (!activeId.value || !mediaUrl?.trim()) return null
    return sendMessage({
      conversationId: activeId.value,
      type,
      mediaUrl: mediaUrl.trim(),
      ...extra,
    })
  }

  function appendOrReplace(vo: ChatMessageVO) {
    const merged = mergePendingReceipts(vo)
    const i = messages.value.findIndex((m) => idEq(m.id, merged.id))
    if (i >= 0) messages.value[i] = merged
    else messages.value = [...messages.value, merged]
    messagesSyncGeneration++
  }

  function replaceMessage(vo: ChatMessageVO) {
    const i = messages.value.findIndex((m) => idEq(m.id, vo.id))
    if (i >= 0) {
      messages.value[i] = mergePendingReceipts(vo)
      messagesSyncGeneration++
    }
  }

  function removeMessageById(messageId: SnowflakeId) {
    messages.value = messages.value.filter((m) => !idEq(m.id, messageId))
    messagesSyncGeneration++
  }

  /** 本地立即显示撤回（接口成功后调用，避免仅依赖 WS） */
  function applyLocalRecall(messageId: SnowflakeId) {
    const m = messages.value.find((x) => idEq(x.id, messageId))
    if (m) {
      m.recalled = 1
      m.content = '该消息已撤回'
      m.mediaUrl = null
      m.mediaCoverUrl = null
      messagesSyncGeneration++
    }
  }

  function bumpDeliveredForIds(ids: SnowflakeId[]) {
    for (const mid of ids) {
      const m = messages.value.find((x) => idEq(x.id, mid))
      if (m) {
        m.deliveredCount = (m.deliveredCount || 0) + 1
      } else {
        const k = String(mid)
        pendingDelivered.value = {
          ...pendingDelivered.value,
          [k]: (pendingDelivered.value[k] || 0) + 1,
        }
      }
    }
  }

  function bumpReadForIds(ids: SnowflakeId[]) {
    for (const mid of ids) {
      const m = messages.value.find((x) => idEq(x.id, mid))
      if (m) {
        m.readCount = (m.readCount || 0) + 1
      } else {
        const k = String(mid)
        pendingRead.value = {
          ...pendingRead.value,
          [k]: (pendingRead.value[k] || 0) + 1,
        }
      }
    }
  }

  async function applyWsPayload(vo: ChatMessageVO) {
    let c = conversations.value.find((x) => idEq(x.conversationId, vo.conversationId))
    if (!c) {
      try {
        await loadConversations()
      } catch {
        /* 列表拉取失败不应阻止展示本条 WS 消息 */
      }
    }
    c = conversations.value.find((x) => idEq(x.conversationId, vo.conversationId))
    if (c) {
      const isOtherConv = !idEq(vo.conversationId, activeId.value)
      patchConversation(vo.conversationId, {
        lastMessagePreview: previewForList(vo),
        updatedAt: vo.createdAt || new Date().toISOString(),
        ...(isOtherConv ? { unreadCount: (c.unreadCount || 0) + 1 } : {}),
      })
    }
    if (idEq(vo.conversationId, activeId.value)) {
      appendOrReplace(vo)
      const uid = selfId.value
      if (uid && !idEq(vo.senderId, uid) && !vo.recalled) {
        stompDeliver([vo.id])
        try {
          await msgApi.markDelivered({ messageIds: [vo.id] })
        } catch {
          /* */
        }
        try {
          await msgApi.markMessagesRead({
            conversationId: vo.conversationId,
            lastReadMessageId: vo.id,
          })
        } catch {
          /* */
        }
      }
    }
  }

  function applyWsRecall(payload: { conversationId?: SnowflakeId; messageId?: SnowflakeId }) {
    const mid = payload.messageId
    const cid = payload.conversationId
    if (mid == null || cid == null) return
    patchConversation(cid, { lastMessagePreview: '[已撤回]' })
    if (!idEq(cid, activeId.value)) return
    const m = messages.value.find((x) => idEq(x.id, mid))
    if (m) {
      m.recalled = 1
      m.content = '该消息已撤回'
      m.mediaUrl = null
      messagesSyncGeneration++
    }
  }

  function applyWsEdit(vo: ChatMessageVO) {
    if (!idEq(vo.conversationId, activeId.value)) return
    replaceMessage(vo)
  }

  function applyWsReaction(vo: ChatMessageVO) {
    if (!idEq(vo.conversationId, activeId.value)) return
    replaceMessage(vo)
  }

  function applyWsDelivered(data: { conversationId?: SnowflakeId; messageIds?: SnowflakeId[] }) {
    if (!idEq(data.conversationId, activeId.value)) return
    const ids = data.messageIds || []
    bumpDeliveredForIds(ids)
  }

  function applyWsRead(data: { conversationId?: SnowflakeId; messageIds?: SnowflakeId[] }) {
    if (!idEq(data.conversationId, activeId.value)) return
    const ids = data.messageIds || []
    bumpReadForIds(ids)
  }

  return {
    conversations,
    conversationsSorted,
    activeId,
    messages,
    loadingList,
    loadingMsg,
    sending,
    beforeCursor,
    activeConversation,
    selfId,
    setSelfId,
    loadConversations,
    selectConversation,
    loadMessages,
    syncNewerMessages,
    upsertConversation,
    sendText,
    sendImage,
    sendMediaMessage,
    sendMessage,
    applyWsPayload,
    applyWsRecall,
    applyWsEdit,
    applyWsReaction,
    applyWsDelivered,
    applyWsRead,
    patchConversation,
    clearMessages,
    clearActive,
    replaceMessage,
    removeMessageById,
    applyLocalRecall,
    syncReceiptsForList,
  }
})
