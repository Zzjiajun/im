<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useContactsStore } from '@/stores/contacts'
import { setLocale, type LocaleTag } from '@/i18n'
import {
  connectStomp,
  disconnectStomp,
  isMessagePayload,
  stompTyping,
} from '@/composables/useStomp'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'
import type {
  RecallWsPayload,
  WsEnvelope,
  ChatMessageVO,
  GroupDetailVO,
  GroupMemberVO,
  SnowflakeId,
} from '@/types/api'
import { normalizeSnowflakeIds, sortSnowflakeIds } from '@/utils/ids'
import { resolveWebSocketUrl } from '@/utils/wsUrl'
import * as convApi from '@/api/conversation'
import * as msgApi from '@/api/message'
import * as uploadApi from '@/api/upload'
import ContactsPanel from '@/components/ContactsPanel.vue'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const chat = useChatStore()
const contacts = useContactsStore()

const wsUrl = resolveWebSocketUrl()
const wsState = ref<'none' | 'live' | 'offline'>('none')
const voiceRecorder = useVoiceRecorder()
const input = ref('')
const listScroll = ref<HTMLElement | null>(null)
const sidePanel = ref<'chats' | 'contacts'>('chats')
const fileInput = ref<HTMLInputElement | null>(null)
const toast = ref('')
const groupModal = ref(false)
const groupDetail = ref<GroupDetailVO | null>(null)
const groupLoading = ref(false)
const groupTab = ref<'info' | 'manage'>('info')
const groupEditName = ref('')
const groupEditNotice = ref('')
const inviteResult = ref('')
const selectedAddFriendIds = ref<SnowflakeId[]>([])
const selectedRemoveMemberIds = ref<SnowflakeId[]>([])
const transferToUid = ref('')
const muteMemberUid = ref('')
const muteUntilStr = ref('')

const convMenuOpen = ref(false)
const convRemark = ref('')
const convDraft = ref('')

const msgMenuMsg = ref<ChatMessageVO | null>(null)
const reportReason = ref('')
const forwardOpen = ref(false)
const forwardTargets = ref<SnowflakeId[]>([])

const replyTo = ref<ChatMessageVO | null>(null)
const mentionAllNext = ref(false)
const mentionUserIds = ref<SnowflakeId[]>([])
const mentionPickerOpen = ref(false)
const mentionMembers = ref<GroupMemberVO[]>([])
const emojiOpen = ref(false)
const emojis = ['😀', '😂', '❤️', '👍', '👎', '🎉', '🙏', '🔥', '✨', '😭', '🤔', '👀']

function onChatMediaError(kind: string, url: string | undefined) {
  console.warn('[im] 媒体加载失败', kind, url)
}

const fileInputVideo = ref<HTMLInputElement | null>(null)
const fileInputAny = ref<HTMLInputElement | null>(null)

const pinnedMessages = ref<ChatMessageVO[]>([])
const inConvSearch = ref('')
const multiSelectMode = ref(false)
const selectedMsgIds = ref<Set<SnowflakeId>>(new Set())
const mergeTitle = ref('')
const mergeForwardOpen = ref(false)
const batchForwardOpen = ref(false)

const receiptModal = ref(false)
const receiptMsg = ref<ChatMessageVO | null>(null)
const receiptReads = ref<Awaited<ReturnType<typeof msgApi.listMessageReads>>>([])
const receiptDelivers = ref<Awaited<ReturnType<typeof msgApi.listMessageDelivers>>>([])

const typingPeerId = ref<SnowflakeId | null>(null)
let typingClearTimer: ReturnType<typeof setTimeout> | null = null
let draftDebounceTimer: ReturnType<typeof setTimeout> | null = null

const filteredMessages = computed(() => {
  const q = inConvSearch.value.trim().toLowerCase()
  if (!q) return chat.messages
  return chat.messages.filter((m) => {
    const t = `${m.content || ''} ${m.senderNickname || ''}`.toLowerCase()
    return t.includes(q)
  })
})

const friendsEligibleForGroup = computed(() => {
  const g = groupDetail.value
  const inGroup = new Set((g?.members || []).map((m) => String(m.userId)))
  return contacts.friends.filter((f) => !inGroup.has(String(f.userId)))
})

const removableGroupMembers = computed(() => {
  const g = groupDetail.value
  if (!g) return []
  const selfId = auth.user?.id
  return g.members.filter((m) => {
    if (String(m.userId) === String(g.ownerId)) return false
    if (selfId != null && String(m.userId) === String(selfId)) return false
    return true
  })
})

function toggleSelectAddFriend(uid: SnowflakeId) {
  const s = String(uid)
  const i = selectedAddFriendIds.value.findIndex((x) => String(x) === s)
  if (i >= 0) selectedAddFriendIds.value.splice(i, 1)
  else selectedAddFriendIds.value.push(uid)
}

function isAddFriendSelected(uid: SnowflakeId) {
  return selectedAddFriendIds.value.some((x) => String(x) === String(uid))
}

function toggleSelectRemoveMember(uid: SnowflakeId) {
  const s = String(uid)
  const i = selectedRemoveMemberIds.value.findIndex((x) => String(x) === s)
  if (i >= 0) selectedRemoveMemberIds.value.splice(i, 1)
  else selectedRemoveMemberIds.value.push(uid)
}

function isRemoveMemberSelected(uid: SnowflakeId) {
  return selectedRemoveMemberIds.value.some((x) => String(x) === String(uid))
}

function onLang(e: Event) {
  const v = (e.target as HTMLSelectElement).value as LocaleTag
  setLocale(v)
}

function initial(name?: string | null) {
  const s = (name || '?').trim()
  return s.slice(0, 1).toUpperCase()
}

/** 头像地址：绝对 URL、本站根路径（/avatars/…）或相对 API 的路径 */
function resolveAvatarUrl(url?: string | null): string | null {
  const u = String(url ?? '').trim()
  if (!u) return null
  if (u.startsWith('http://') || u.startsWith('https://') || u.startsWith('//')) return u
  if (u.startsWith('/')) return u
  const base = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')
  if (base.startsWith('http')) {
    return `${base}/${u.replace(/^\/+/, '')}`
  }
  return `${base}/${u.replace(/^\/+/, '')}`
}

/** 消息气泡旁头像：自己优先用资料里最新头像，避免列表未重拉时仍显示旧图 */
function msgAvatarUrl(m: ChatMessageVO): string | null {
  if (auth.user?.id != null && idEq(m.senderId, auth.user.id) && auth.user.avatar) {
    return resolveAvatarUrl(auth.user.avatar)
  }
  return resolveAvatarUrl(m.senderAvatar)
}

function isSelf(m: ChatMessageVO) {
  return m.senderId === auth.user?.id
}

function idEq(a: SnowflakeId | null | undefined, b: SnowflakeId | null | undefined) {
  if (a == null || b == null) return false
  return String(a) === String(b)
}

function mergePreview(c?: string | null) {
  if (!c) return `[${t('chat.mergeMsg')}]`
  try {
    const o = JSON.parse(c) as { title?: string }
    return o.title ? `[${o.title}]` : `[${t('chat.mergeMsg')}]`
  } catch {
    return `[${t('chat.mergeMsg')}]`
  }
}

function locationPreview(c?: string | null) {
  if (!c) return '[📍]'
  try {
    const o = JSON.parse(c) as { name?: string; address?: string }
    return o.name || o.address || '[📍]'
  } catch {
    return '[📍]'
  }
}

async function scrollBottom() {
  await nextTick()
  const el = listScroll.value
  if (el) el.scrollTop = el.scrollHeight
}

watch(
  () => chat.messages.length,
  () => scrollBottom()
)

function handleWs(env: WsEnvelope<unknown>) {
  if (env.event === 'MESSAGE' && isMessagePayload(env.data)) {
    void chat.applyWsPayload(env.data)
    return
  }
  if (env.event === 'RECALL' && env.data && typeof env.data === 'object') {
    chat.applyWsRecall(env.data as RecallWsPayload)
    return
  }
  if (env.event === 'EDIT' && isMessagePayload(env.data)) {
    chat.applyWsEdit(env.data)
    return
  }
  if (env.event === 'REACTION' && isMessagePayload(env.data)) {
    chat.applyWsReaction(env.data)
    return
  }
  if (env.event === 'DELIVERED' && env.data && typeof env.data === 'object') {
    const d = env.data as { conversationId?: SnowflakeId; messageIds?: SnowflakeId[] }
    chat.applyWsDelivered(d)
    return
  }
  if (env.event === 'READ' && env.data && typeof env.data === 'object') {
    const d = env.data as { conversationId?: SnowflakeId; messageIds?: SnowflakeId[] }
    chat.applyWsRead(d)
    return
  }
  if (env.event === 'TYPING' && env.data && typeof env.data === 'object') {
    const d = env.data as { conversationId?: SnowflakeId; userId?: SnowflakeId; typing?: boolean }
    if (!idEq(d.conversationId, chat.activeId)) return
    if (d.userId === auth.user?.id) return
    if (d.typing) {
      typingPeerId.value = d.userId ?? null
      if (typingClearTimer) clearTimeout(typingClearTimer)
      typingClearTimer = setTimeout(() => {
        typingPeerId.value = null
      }, 4500)
    } else {
      typingPeerId.value = null
    }
    return
  }
  if (env.event === 'MESSAGE_PINNED' && isMessagePayload(env.data)) {
    const vo = env.data
    if (idEq(vo.conversationId, chat.activeId)) {
      void msgApi.listPinnedMessages(vo.conversationId).then((list) => {
        pinnedMessages.value = list
      })
    }
  }
}

function bindWs(token: string | null) {
  disconnectStomp()
  if (!token || !wsUrl) {
    wsState.value = 'none'
    return
  }
  wsState.value = 'offline'
  connectStomp(wsUrl, token, handleWs, {
    onConnected: () => {
      wsState.value = 'live'
      const id = chat.activeId
      if (id) void chat.syncNewerMessages(id)
    },
    onDisconnected: () => {
      wsState.value = 'offline'
    },
  })
}

async function handleOpenConvQuery() {
  const raw = route.query.openConv
  const id =
    typeof raw === 'string' ? raw : Array.isArray(raw) && raw[0] ? String(raw[0]) : ''
  if (!id) return
  sidePanel.value = 'chats'
  try {
    await chat.loadConversations()
    await chat.selectConversation(id as SnowflakeId)
  } catch {
    /* 会话可能已隐藏或无权访问 */
  }
  await router.replace({ path: '/', query: {} })
  scrollBottom()
}

onMounted(async () => {
  if (!auth.user) {
    await auth.refreshProfile()
  }
  chat.setSelfId(auth.user?.id ?? null)
  await chat.loadConversations()
  bindWs(auth.token)
  await handleOpenConvQuery()
  scrollBottom()
})

watch(
  () => route.query.openConv,
  (q) => {
    if (q) void handleOpenConvQuery()
  }
)

watch(
  () => auth.user?.id,
  (id) => chat.setSelfId(id ?? null)
)

watch(
  () => chat.activeConversation?.conversationId,
  async (cid) => {
    if (cid == null) return
    input.value = chat.activeConversation?.draftContent || ''
    replyTo.value = null
    mentionAllNext.value = false
    mentionUserIds.value = []
    inConvSearch.value = ''
    multiSelectMode.value = false
    selectedMsgIds.value = new Set()
    try {
      pinnedMessages.value = await msgApi.listPinnedMessages(cid)
    } catch {
      pinnedMessages.value = []
    }
  }
)

watch(
  () => auth.token,
  (tok) => bindWs(tok)
)

onUnmounted(() => {
  // 不断开 STOMP：切到个人资料等页面时仍需接收推送；仅退出登录时 disconnect（见 logout）
  voiceRecorder.cancel()
  if (typingClearTimer) clearTimeout(typingClearTimer)
  if (draftDebounceTimer) clearTimeout(draftDebounceTimer)
})

async function pick(id: SnowflakeId) {
  sidePanel.value = 'chats'
  await chat.selectConversation(id)
  scrollBottom()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

function onComposerInput() {
  const id = chat.activeId
  if (!id) return
  stompTyping(id, true)
  if (draftDebounceTimer) clearTimeout(draftDebounceTimer)
  draftDebounceTimer = setTimeout(async () => {
    try {
      await convApi.updateConversationDraft(id, { draftContent: input.value })
    } catch {
      /* 忽略草稿同步错误 */
    }
  }, 1200)
}

function onComposerBlur() {
  const id = chat.activeId
  if (id) stompTyping(id, false)
}

async function loadMoreWithScroll() {
  const el = listScroll.value
  const prev = el?.scrollHeight ?? 0
  const aid = chat.activeId
  if (!aid) return
  await chat.loadMessages(aid, true)
  await nextTick()
  if (el) el.scrollTop = el.scrollHeight - prev
}

async function send() {
  const text = input.value.trim()
  if (!text) return
  input.value = ''
  const id = chat.activeId
  if (id) stompTyping(id, false)
  try {
    await chat.sendText(text, {
      replyMessageId: replyTo.value?.id,
      mentionAll: mentionAllNext.value || undefined,
      mentionUserIds: mentionUserIds.value.length ? mentionUserIds.value : undefined,
    })
    replyTo.value = null
    mentionAllNext.value = false
    mentionUserIds.value = []
    scrollBottom()
  } catch (e: unknown) {
    input.value = text
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function toggleVoiceRecord() {
  if (!chat.activeId || chat.sending) return
  if (!voiceRecorder.recording.value) {
    try {
      await voiceRecorder.start()
    } catch (e: unknown) {
      showToast(e instanceof Error ? e.message : t('chat.voiceMicDenied'))
    }
  } else {
    const blob = await voiceRecorder.stop()
    if (!blob) return
    try {
      const ext = blob.type.includes('mpeg') || blob.type.includes('mp4') ? 'm4a' : 'webm'
      const file = new File([blob], `voice.${ext}`, { type: blob.type || 'audio/webm' })
      const media = await uploadApi.uploadMedia(file, 'VOICE')
      await chat.sendMediaMessage('VOICE', media.url, { replyMessageId: replyTo.value?.id })
      replyTo.value = null
      scrollBottom()
    } catch (e: unknown) {
      showToast(e instanceof Error ? e.message : String(e))
    }
  }
}

function insertEmoji(ch: string) {
  input.value += ch
  emojiOpen.value = false
}

function toggleMentionMember(uid: SnowflakeId) {
  const i = mentionUserIds.value.indexOf(uid)
  if (i >= 0) mentionUserIds.value = mentionUserIds.value.filter((x) => x !== uid)
  else mentionUserIds.value = [...mentionUserIds.value, uid]
}

async function openReceipts(m: ChatMessageVO) {
  receiptMsg.value = m
  receiptModal.value = true
  try {
    const [r, d] = await Promise.all([
      msgApi.listMessageReads(m.id),
      msgApi.listMessageDelivers(m.id),
    ])
    receiptReads.value = r
    receiptDelivers.value = d
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function openReceiptsFromMenu() {
  const m = msgMenuMsg.value
  if (!m) return
  msgMenuMsg.value = null
  await openReceipts(m)
}

function toggleMsgSelect(m: ChatMessageVO) {
  if (!multiSelectMode.value) return
  const next = new Set(selectedMsgIds.value)
  if (next.has(m.id)) next.delete(m.id)
  else next.add(m.id)
  selectedMsgIds.value = next
}

async function doMergeForward() {
  const ids = sortSnowflakeIds([...selectedMsgIds.value])
  if (!ids.length || !mergeTitle.value.trim()) {
    showToast(t('chat.mergeNeedTitle'))
    return
  }
  if (!forwardTargets.value.length) {
    showToast(t('chat.pickTargets'))
    return
  }
  const targs = forwardTargets.value
  try {
    await msgApi.mergeForwardMessages({
      sourceMessageIds: ids,
      targetConversationIds: targs,
      title: mergeTitle.value.trim(),
    })
    mergeForwardOpen.value = false
    multiSelectMode.value = false
    selectedMsgIds.value = new Set()
    mergeTitle.value = ''
    forwardTargets.value = []
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function doBatchForward() {
  const ids = sortSnowflakeIds([...selectedMsgIds.value])
  if (!ids.length || !forwardTargets.value.length) return
  try {
    await msgApi.batchForwardMessages({
      sourceMessageIds: ids,
      targetConversationIds: forwardTargets.value,
    })
    batchForwardOpen.value = false
    multiSelectMode.value = false
    selectedMsgIds.value = new Set()
    forwardTargets.value = []
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

function showToast(msg: string) {
  toast.value = msg
  setTimeout(() => {
    toast.value = ''
  }, 2800)
}

function formatConvTime(s?: string | null) {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
  }
  return `${d.getMonth() + 1}/${d.getDate()}`
}

function scrollToMessage(mid: SnowflakeId) {
  const el = listScroll.value?.querySelector(`[data-mid="${mid}"]`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function openChatWithFriend(userId: SnowflakeId) {
  try {
    const vo = await convApi.createSingleConversation(userId)
    sidePanel.value = 'chats'
    chat.upsertConversation(vo)
    await chat.loadConversations()
    await chat.selectConversation(vo.conversationId)
    scrollBottom()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

function openChatsPanel() {
  sidePanel.value = 'chats'
  void chat.loadConversations()
}

function openContacts() {
  sidePanel.value = 'contacts'
  void contacts.loadFriendTags()
  void contacts.loadFriendsAndRequests()
}

function triggerImage() {
  fileInput.value?.click()
}

async function onImageSelected(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  el.value = ''
  if (!f) return
  if (!f.type.startsWith('image/')) {
    showToast(t('common.error'))
    return
  }
  try {
    const media = await uploadApi.uploadMedia(f, 'IMAGE')
    await chat.sendImage(media.url, {
      replyMessageId: replyTo.value?.id,
    })
    replyTo.value = null
    scrollBottom()
  } catch (err: unknown) {
    showToast(err instanceof Error ? err.message : String(err))
  }
}

async function onVideoSelected(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  el.value = ''
  if (!f) return
  if (!f.type.startsWith('video/')) {
    showToast(t('chat.videoOnly'))
    return
  }
  try {
    const media = await uploadApi.uploadMedia(f, 'VIDEO')
    await chat.sendMediaMessage('VIDEO', media.url, { replyMessageId: replyTo.value?.id })
    replyTo.value = null
    scrollBottom()
  } catch (err: unknown) {
    showToast(err instanceof Error ? err.message : String(err))
  }
}

async function onAnyFileSelected(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  el.value = ''
  if (!f) return
  try {
    const media = await uploadApi.uploadMedia(f, 'FILE')
    await chat.sendMediaMessage('FILE', media.url, {
      replyMessageId: replyTo.value?.id,
    })
    replyTo.value = null
    scrollBottom()
  } catch (err: unknown) {
    showToast(err instanceof Error ? err.message : String(err))
  }
}

function replySnippet(r: ChatMessageVO['replyMessage']) {
  if (!r) return ''
  if (r.content) return r.content.slice(0, 80)
  if (r.type === 'IMAGE') return '[图片]'
  return `[${r.type || 'msg'}]`
}

function messageSnippet(m: ChatMessageVO) {
  if (m.content) return m.content.slice(0, 80)
  if (m.type === 'IMAGE') return '[图片]'
  if (m.type === 'VIDEO') return '[视频]'
  return `[${m.type}]`
}

function pinnedPreview(p: ChatMessageVO) {
  if (p.type === 'MERGE') return mergePreview(p.content)
  return messageSnippet(p).slice(0, 28)
}

async function openMentionPicker() {
  const id = chat.activeId
  if (!id || chat.activeConversation?.type !== 'GROUP') return
  try {
    const detail = await convApi.fetchGroupDetail(id)
    mentionMembers.value = detail.members || []
    mentionPickerOpen.value = true
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

function toggleMultiMode() {
  multiSelectMode.value = !multiSelectMode.value
  selectedMsgIds.value = new Set()
}

function openMergeForward() {
  forwardTargets.value = []
  mergeTitle.value = ''
  mergeForwardOpen.value = true
}

function openBatchForward() {
  forwardTargets.value = []
  batchForwardOpen.value = true
}

function clearMentions() {
  mentionAllNext.value = false
  mentionUserIds.value = []
}

async function openGroupModal(options?: { preferManage?: boolean }) {
  const c = chat.activeConversation
  if (!c || c.type !== 'GROUP') return
  groupModal.value = true
  groupTab.value = 'info'
  inviteResult.value = ''
  selectedAddFriendIds.value = []
  selectedRemoveMemberIds.value = []
  void contacts.loadFriendsAndRequests()
  groupDetail.value = null
  groupLoading.value = true
  try {
    groupDetail.value = await convApi.fetchGroupDetail(c.conversationId)
    groupEditName.value = groupDetail.value.name || ''
    groupEditNotice.value = groupDetail.value.notice || ''
    if (
      options?.preferManage &&
      auth.user?.id != null &&
      String(auth.user.id) === String(groupDetail.value.ownerId)
    ) {
      groupTab.value = 'manage'
    }
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
    groupModal.value = false
  } finally {
    groupLoading.value = false
  }
}

function openGroupInvite() {
  void openGroupModal({ preferManage: true })
}

async function reloadGroupDetail() {
  const id = chat.activeId
  if (!id) return
  try {
    groupDetail.value = await convApi.fetchGroupDetail(id)
    groupEditName.value = groupDetail.value.name || ''
    groupEditNotice.value = groupDetail.value.notice || ''
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

const isGroupOwner = () =>
  !!(groupDetail.value && auth.user?.id === groupDetail.value.ownerId)

async function saveGroupProfile() {
  const id = chat.activeId
  if (!id || !isGroupOwner()) return
  try {
    await convApi.updateGroupProfile(id, {
      name: groupEditName.value || undefined,
      notice: groupEditNotice.value || undefined,
    })
    await reloadGroupDetail()
    await chat.loadConversations()
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function genGroupInvite() {
  const id = chat.activeId
  if (!id || !isGroupOwner()) return
  try {
    const vo = await convApi.createGroupInvite(id, {})
    inviteResult.value = vo.token
    showToast(t('chat.inviteCopied'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function toggleMuteAll() {
  const id = chat.activeId
  if (!id || !groupDetail.value || !isGroupOwner()) return
  try {
    await convApi.setGroupMuteAll(id, { muteAll: !groupDetail.value.muteAll })
    await reloadGroupDetail()
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function addGroupMembers() {
  const id = chat.activeId
  if (!id || !isGroupOwner()) return
  const ids = normalizeSnowflakeIds(selectedAddFriendIds.value)
  if (!ids.length) {
    showToast(t('chat.pickFriendsToAdd'))
    return
  }
  try {
    await convApi.addGroupMembers(id, { memberIds: ids })
    selectedAddFriendIds.value = []
    await reloadGroupDetail()
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function removeGroupMembers() {
  const id = chat.activeId
  if (!id || !isGroupOwner()) return
  const ids = normalizeSnowflakeIds(selectedRemoveMemberIds.value)
  if (!ids.length) {
    showToast(t('chat.pickMembersToRemove'))
    return
  }
  try {
    await convApi.removeGroupMembers(id, { memberIds: ids })
    selectedRemoveMemberIds.value = []
    await reloadGroupDetail()
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function leaveCurrentGroup() {
  const id = chat.activeId
  if (!id) return
  try {
    await convApi.leaveGroup(id)
    groupModal.value = false
    await chat.loadConversations()
    chat.clearActive()
    showToast(t('chat.leftGroup'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function transferGroupOwner() {
  const id = chat.activeId
  const tid = transferToUid.value.trim()
  if (!id || !tid || !/^\d+$/.test(tid) || !isGroupOwner()) return
  try {
    await convApi.transferGroupOwner(id, { targetUserId: tid })
    transferToUid.value = ''
    await reloadGroupDetail()
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function muteOneMember() {
  const id = chat.activeId
  const uid = muteMemberUid.value.trim()
  if (!id || !uid || !/^\d+$/.test(uid) || !isGroupOwner()) return
  const mutedUntil = muteUntilStr.value
    ? new Date(muteUntilStr.value).toISOString()
    : null
  try {
    await convApi.muteGroupMember(id, { userId: uid, mutedUntil })
    muteMemberUid.value = ''
    muteUntilStr.value = ''
    await reloadGroupDetail()
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

function openConvMenu() {
  const c = chat.activeConversation
  if (!c) return
  convRemark.value = ''
  convDraft.value = c.draftContent || ''
  convMenuOpen.value = !convMenuOpen.value
}

async function applyConvSettings(patch: { pinned?: boolean; muted?: boolean; archived?: boolean }) {
  const id = chat.activeId
  if (!id) return
  convMenuOpen.value = false
  try {
    await convApi.updateConversationSettings(id, patch)
    chat.patchConversation(id, patch)
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function saveConvRemark() {
  const id = chat.activeId
  if (!id) return
  try {
    await convApi.updateConversationRemark(id, { remark: convRemark.value || undefined })
    convMenuOpen.value = false
    await chat.loadConversations()
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function saveConvDraft() {
  const id = chat.activeId
  if (!id) return
  try {
    await convApi.updateConversationDraft(id, { draftContent: convDraft.value })
    convMenuOpen.value = false
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function hideCurrentConversation() {
  const id = chat.activeId
  if (!id) return
  convMenuOpen.value = false
  try {
    await convApi.hideConversation(id)
    await chat.loadConversations()
    chat.clearActive()
    showToast(t('chat.hidden'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function clearCurrentHistory() {
  const id = chat.activeId
  if (!id) return
  convMenuOpen.value = false
  try {
    await convApi.clearConversationHistory(id, {})
    chat.clearMessages()
    await chat.loadMessages(id, false)
    showToast(t('chat.cleared'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function syncLastCursor() {
  const id = chat.activeId
  const last = chat.messages[chat.messages.length - 1]
  if (!id || !last) return
  convMenuOpen.value = false
  try {
    await convApi.syncConversationCursor(id, { messageId: last.id })
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

function openMsgMenu(m: ChatMessageVO) {
  msgMenuMsg.value = m
}

function replyToMessage(m: ChatMessageVO | null) {
  if (!m) return
  replyTo.value = m
  msgMenuMsg.value = null
}

function toggleForwardTarget(cid: SnowflakeId) {
  const i = forwardTargets.value.indexOf(cid)
  if (i >= 0) forwardTargets.value = forwardTargets.value.filter((x) => x !== cid)
  else forwardTargets.value = [...forwardTargets.value, cid]
}

async function doForwardMessage() {
  const m = msgMenuMsg.value
  if (!m || !forwardTargets.value.length) return
  try {
    await msgApi.forwardMessages({
      sourceMessageId: m.id,
      targetConversationIds: forwardTargets.value,
    })
    forwardOpen.value = false
    msgMenuMsg.value = null
    forwardTargets.value = []
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function recallSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m) return
  if (!window.confirm(t('chat.confirmRecall'))) return
  try {
    await msgApi.recallMessage({ messageId: m.id })
    chat.applyLocalRecall(m.id)
    msgMenuMsg.value = null
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function editSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m || m.type !== 'TEXT') return
  const text = window.prompt(t('chat.newContent'), m.content || '')
  if (text == null || !text.trim()) return
  try {
    const vo = await msgApi.editMessage({ messageId: m.id, content: text.trim() })
    chat.replaceMessage(vo)
    msgMenuMsg.value = null
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function favoriteSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m) return
  try {
    await msgApi.favoriteMessage({ messageId: m.id })
    msgMenuMsg.value = null
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function reportSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m || !reportReason.value.trim()) return
  try {
    await msgApi.reportMessage({
      messageId: m.id,
      reason: reportReason.value.trim(),
    })
    reportReason.value = ''
    msgMenuMsg.value = null
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function deleteSelfSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m) return
  if (!window.confirm(t('chat.confirmDeleteSelf'))) return
  try {
    await msgApi.deleteMessagesForSelf({ messageIds: [m.id] })
    chat.removeMessageById(m.id)
    msgMenuMsg.value = null
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function pinSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m) return
  try {
    await msgApi.pinMessage({ messageId: m.id })
    msgMenuMsg.value = null
    if (chat.activeId) {
      pinnedMessages.value = await msgApi.listPinnedMessages(chat.activeId)
    }
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function unpinSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m) return
  try {
    await msgApi.unpinMessage({ messageId: m.id })
    msgMenuMsg.value = null
    if (chat.activeId) {
      pinnedMessages.value = await msgApi.listPinnedMessages(chat.activeId)
    }
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

function copyMessageText() {
  const m = msgMenuMsg.value
  if (!m?.content) return
  void navigator.clipboard.writeText(m.content)
  msgMenuMsg.value = null
  showToast(t('chat.copied'))
}

async function reactSelectedMessage() {
  const m = msgMenuMsg.value
  if (!m) return
  try {
    await msgApi.reactMessage({ messageId: m.id, reactionType: '👍' })
    msgMenuMsg.value = null
    showToast(t('chat.saved'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function logout() {
  disconnectStomp()
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <header class="top">
      <div class="brand">
        <span class="dot" />
        <span class="title">{{ t('app.title') }}</span>
        <span
          v-if="wsUrl"
          class="ws-pill"
          :class="{ 'ws-ok': wsState === 'live', 'ws-bad': wsState === 'offline' }"
          :title="wsState === 'live' ? t('chat.wsLive') : t('chat.wsOffline')"
        >
          {{ wsState === 'live' ? t('chat.wsLiveShort') : t('chat.wsOfflineShort') }}
        </span>
      </div>
      <div class="tools">
        <label class="lang-wrap">
          <span class="sr">{{ t('common.language') }}</span>
          <select
            :value="locale === 'en' ? 'en' : 'zh-CN'"
            class="lang"
            @change="onLang"
          >
            <option value="zh-CN">{{ t('common.zh') }}</option>
            <option value="en">{{ t('common.en') }}</option>
          </select>
        </label>
        <span class="nick">{{ auth.user?.nickname }}</span>
        <button type="button" class="link" @click="logout">{{ t('auth.logout') }}</button>
      </div>
    </header>

    <div class="body">
      <nav class="rail" aria-label="nav">
        <button
          type="button"
          class="rail-btn"
          :class="{ on: sidePanel === 'chats' }"
          :title="t('chat.conversations')"
          @click="openChatsPanel"
        >
          💬
        </button>
        <button
          type="button"
          class="rail-btn"
          :class="{ on: sidePanel === 'contacts' }"
          :title="t('chat.contacts')"
          @click="openContacts"
        >
          👤
        </button>
        <button
          type="button"
          class="rail-btn"
          :title="t('chat.favoritesNav')"
          @click="router.push('/favorites')"
        >
          ⭐
        </button>
        <button
          type="button"
          class="rail-btn"
          :title="t('groups.title')"
          @click="router.push('/groups')"
        >
          👥
        </button>
        <button type="button" class="rail-btn" :title="t('chat.profile')" @click="router.push('/profile')">
          ⚙
        </button>
        <button type="button" class="rail-btn" :title="t('chat.tools')" @click="router.push('/tools')">
          🧰
        </button>
      </nav>

      <aside class="side">
        <div class="side-head">
          {{ sidePanel === 'chats' ? t('chat.conversations') : t('chat.contacts') }}
        </div>
        <template v-if="sidePanel === 'chats'">
          <div v-if="chat.loadingList" class="hint">{{ t('common.loading') }}</div>
          <ul v-else class="conv-list">
            <li
              v-for="c in chat.conversationsSorted"
              :key="String(c.conversationId)"
              :class="['conv-item', { active: String(chat.activeId) === String(c.conversationId) }]"
              @click="pick(c.conversationId)"
            >
              <div class="avatar">
                <img v-if="resolveAvatarUrl(c.displayAvatar)" :src="resolveAvatarUrl(c.displayAvatar)!" alt="" />
                <template v-else>{{ initial(c.displayName) }}</template>
              </div>
              <div class="meta">
                <div class="row1">
                  <span class="name-wrap">
                    <span v-if="c.pinned" class="pin-ic" title="pinned">📌</span>
                    <span class="name">{{ c.displayName }}</span>
                    <span v-if="c.type === 'GROUP'" class="g-tag">{{ t('chat.groupBadge') }}</span>
                  </span>
                  <span class="conv-time">{{ formatConvTime(c.updatedAt) }}</span>
                  <span v-if="c.unreadCount" class="badge">{{ c.unreadCount }}</span>
                </div>
                <div class="preview">
                  <template v-if="c.draftContent"><span class="draft-tag">{{ t('chat.draftLabel') }}</span></template>
                  {{ c.draftContent || c.lastMessagePreview || '—' }}
                </div>
              </div>
            </li>
          </ul>
        </template>
        <ContactsPanel v-else @open-chat="openChatWithFriend" />
      </aside>

      <main class="main">
        <template v-if="chat.activeConversation">
          <div class="chat-head">
            <span class="chat-title">{{ chat.activeConversation.displayName }}</span>
            <span v-if="chat.activeConversation.type === 'GROUP'" class="g-tag sm">{{
              t('chat.groupBadge')
            }}</span>
            <span v-if="chat.activeConversation.muted" class="tag">{{ t('chat.muted') }}</span>
            <input
              v-model="inConvSearch"
              class="insearch"
              type="search"
              :placeholder="t('chat.searchInConv')"
            />
            <button type="button" class="info-btn" :class="{ on: multiSelectMode }" @click="toggleMultiMode">
              {{ multiSelectMode ? t('chat.cancelMulti') : t('chat.multiSelect') }}
            </button>
            <button
              v-if="chat.activeConversation.type === 'GROUP'"
              type="button"
              class="info-btn"
              @click="openMentionPicker"
            >
              @
            </button>
            <button
              v-if="multiSelectMode && selectedMsgIds.size"
              type="button"
              class="info-btn"
              @click="openMergeForward"
            >
              {{ t('chat.mergeForward') }}
            </button>
            <button
              v-if="multiSelectMode && selectedMsgIds.size"
              type="button"
              class="info-btn"
              @click="openBatchForward"
            >
              {{ t('chat.batchForward') }}
            </button>
            <button
              v-if="chat.activeConversation.type === 'GROUP'"
              type="button"
              class="info-btn"
              @click="openGroupInvite"
            >
              {{ t('chat.inviteToGroup') }}
            </button>
            <button
              v-if="chat.activeConversation.type === 'GROUP'"
              type="button"
              class="info-btn"
              @click="openGroupModal()"
            >
              {{ t('chat.groupInfo') }}
            </button>
            <div class="conv-menu-wrap">
              <button type="button" class="info-btn" @click="openConvMenu">⋯</button>
              <div v-if="convMenuOpen" class="conv-dropdown">
                <button type="button" @click="applyConvSettings({ pinned: !chat.activeConversation.pinned })">
                  {{ chat.activeConversation.pinned ? t('chat.unpin') : t('chat.pin') }}
                </button>
                <button type="button" @click="applyConvSettings({ muted: !chat.activeConversation.muted })">
                  {{ chat.activeConversation.muted ? t('chat.unmute') : t('chat.mute') }}
                </button>
                <button type="button" @click="applyConvSettings({ archived: !chat.activeConversation.archived })">
                  {{ chat.activeConversation.archived ? t('chat.unarchive') : t('chat.archive') }}
                </button>
                <input v-model="convRemark" class="dd-input" :placeholder="t('chat.convRemark')" />
                <button type="button" @click="saveConvRemark">{{ t('chat.saveRemark') }}</button>
                <input v-model="convDraft" class="dd-input" :placeholder="t('chat.draft')" />
                <button type="button" @click="saveConvDraft">{{ t('chat.saveDraft') }}</button>
                <button type="button" @click="syncLastCursor">{{ t('chat.syncCursor') }}</button>
                <button type="button" class="dd-danger" @click="clearCurrentHistory">{{ t('chat.clearHistory') }}</button>
                <button type="button" class="dd-danger" @click="hideCurrentConversation">{{ t('chat.hideConv') }}</button>
              </div>
            </div>
          </div>
          <div v-if="typingPeerId" class="typing-bar">{{ t('chat.typing') }}</div>
          <div v-if="pinnedMessages.length" class="pin-strip">
            <span class="pin-strip-t">{{ t('chat.pinnedBar') }}</span>
            <button
              v-for="p in pinnedMessages"
              :key="p.id"
              type="button"
              class="pin-chip"
              @click="scrollToMessage(p.id)"
            >
              {{ pinnedPreview(p) }}
            </button>
          </div>
          <div ref="listScroll" class="msg-scroll">
            <div class="load-row">
              <button
                v-if="chat.beforeCursor"
                type="button"
                class="load-more"
                :disabled="chat.loadingMsg"
                @click="loadMoreWithScroll"
              >
                {{ chat.loadingMsg ? t('common.loading') : t('chat.loadMore') }}
              </button>
            </div>
            <div v-if="!chat.messages.length && !chat.loadingMsg" class="empty">
              {{ t('chat.emptyMessages') }}
            </div>
            <div
              v-for="m in filteredMessages"
              :key="m.id"
              :data-mid="m.id"
              :class="[
                'msg-row',
                isSelf(m) ? 'self' : 'other',
                { 'sel-on': multiSelectMode && selectedMsgIds.has(m.id) },
              ]"
              @click="multiSelectMode ? toggleMsgSelect(m) : undefined"
            >
              <div class="avatar sm">
                <img v-if="msgAvatarUrl(m)" :src="msgAvatarUrl(m)!" alt="" />
                <template v-else>{{ initial(m.senderNickname) }}</template>
              </div>
              <div class="bubble-wrap">
                <div class="who">
                  {{ isSelf(m) ? t('chat.you') : m.senderNickname }}
                  <span v-if="m.edited" class="edited-tag">{{ t('chat.edited') }}</span>
                  <span v-if="m.mentionAll" class="at-all">{{ t('chat.mentionAll') }}</span>
                  <button type="button" class="msg-act" @click.stop="openMsgMenu(m)">⋮</button>
                </div>
                <div
                  v-if="m.replyMessage"
                  class="reply-quote"
                  @click.stop="m.replyMessage.messageId && scrollToMessage(m.replyMessage.messageId)"
                >
                  ↪ {{ m.replyMessage.senderNickname }}: {{ replySnippet(m.replyMessage) }}
                </div>
                <div class="bubble">
                  <template v-if="m.recalled">[{{ t('chat.recalled') }}]</template>
                  <template v-else-if="m.type === 'IMAGE' && m.mediaUrl">
                    <img
                      :src="m.mediaUrl"
                      class="img"
                      alt=""
                      @error="onChatMediaError('IMAGE', m.mediaUrl)"
                    />
                  </template>
                  <template v-else-if="m.type === 'VIDEO' && m.mediaUrl">
                    <video
                      class="vid"
                      controls
                      :src="m.mediaUrl"
                      @error="onChatMediaError('VIDEO', m.mediaUrl)"
                    />
                  </template>
                  <template v-else-if="m.type === 'VOICE' && m.mediaUrl">
                    <audio
                      class="aud aud-voice"
                      controls
                      :src="m.mediaUrl"
                      @error="onChatMediaError('VOICE', m.mediaUrl)"
                    />
                  </template>
                  <template v-else-if="m.type === 'MERGE'">{{ mergePreview(m.content) }}</template>
                  <template v-else-if="m.type === 'LOCATION'">{{ locationPreview(m.content) }}</template>
                  <template v-else-if="m.type === 'CONTACT'">[{{ t('chat.contactCard') }}]</template>
                  <template v-else-if="m.type === 'FILE'">
                    <a v-if="m.mediaUrl" :href="m.mediaUrl" target="_blank" rel="noopener">📎 {{ t('chat.downloadFile') }}</a>
                    <span v-else>{{ m.content }}</span>
                  </template>
                  <template v-else>{{ m.content || t('chat.otherMsg') }}</template>
                </div>
                <div v-if="m.reactions?.length" class="react-row">
                  <span v-for="(rx, ri) in m.reactions" :key="ri" class="react-pill">
                    {{ rx.reactionType }}<template v-if="(rx.count || 0) > 1">×{{ rx.count }}</template>
                  </span>
                </div>
                <button
                  v-if="isSelf(m) && !m.recalled && (m.readCount || m.deliveredCount)"
                  type="button"
                  class="rcpt"
                  @click.stop="openReceipts(m)"
                >
                  <template v-if="m.readCount">{{ t('chat.read') }} {{ m.readCount }}</template>
                  <template v-if="m.deliveredCount">{{ t('chat.delivered') }} {{ m.deliveredCount }}</template>
                </button>
              </div>
            </div>
          </div>
          <div class="composer">
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              class="hidden-file"
              @change="onImageSelected"
            />
            <input
              ref="fileInputVideo"
              type="file"
              accept="video/*"
              class="hidden-file"
              @change="onVideoSelected"
            />
            <input ref="fileInputAny" type="file" class="hidden-file" @change="onAnyFileSelected" />
            <div v-if="replyTo" class="reply-bar">
              <span>{{ t('chat.replying') }}: {{ messageSnippet(replyTo).slice(0, 48) }}</span>
              <button type="button" class="x-sm" @click="replyTo = null">×</button>
            </div>
            <div v-if="mentionAllNext || mentionUserIds.length" class="mention-bar">
              <span v-if="mentionAllNext">{{ t('chat.mentionAll') }} · </span>
              <span v-if="mentionUserIds.length">@{{ mentionUserIds.join(',') }}</span>
              <button type="button" class="x-sm" @click="clearMentions">×</button>
            </div>
            <div class="tool-bar">
              <button type="button" class="tool" :disabled="chat.sending" @click="triggerImage">
                {{ t('chat.sendImage') }}
              </button>
              <button type="button" class="tool" :disabled="chat.sending" @click="fileInputVideo?.click()">
                {{ t('chat.sendVideo') }}
              </button>
              <button type="button" class="tool" :disabled="chat.sending" @click="fileInputAny?.click()">
                {{ t('chat.sendFile') }}
              </button>
              <button
                type="button"
                class="tool"
                :class="{ 'voice-on': voiceRecorder.recording.value }"
                :disabled="chat.sending"
                @click="toggleVoiceRecord"
              >
                {{ voiceRecorder.recording.value ? t('chat.voiceStopSend') : t('chat.voice') }}
              </button>
              <button type="button" class="tool" @click="emojiOpen = !emojiOpen">{{ t('chat.emoji') }}</button>
              <label class="tool chk"
                ><input v-model="mentionAllNext" type="checkbox" />
                {{ t('chat.mentionAll') }}</label
              >
            </div>
            <div v-if="emojiOpen" class="emoji-pop">
              <button v-for="em in emojis" :key="em" type="button" class="em-btn" @click="insertEmoji(em)">
                {{ em }}
              </button>
            </div>
            <div class="composer-row">
              <textarea
                v-model="input"
                class="ta"
                rows="3"
                :placeholder="t('chat.inputPlaceholder')"
                @keydown="onKeydown"
                @input="onComposerInput"
                @blur="onComposerBlur"
              />
              <button
                type="button"
                class="wx-btn-primary send"
                :disabled="chat.sending"
                @click="send"
              >
                {{ t('chat.send') }}
              </button>
            </div>
          </div>
        </template>
        <div v-else class="placeholder">
          <p>{{ t('chat.noConversation') }}</p>
        </div>
      </main>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>

    <div v-if="groupModal" class="modal-mask" @click.self="groupModal = false">
      <div class="modal modal-wide">
        <div class="modal-head">
          <span>{{ t('chat.groupInfo') }}</span>
          <button type="button" class="modal-close" @click="groupModal = false">
            {{ t('common.close') }}
          </button>
        </div>
        <div v-if="groupLoading" class="modal-body">{{ t('common.loading') }}</div>
        <div v-else-if="groupDetail" class="modal-body">
          <div class="tabs">
            <button
              type="button"
              :class="{ on: groupTab === 'info' }"
              @click="groupTab = 'info'"
            >
              {{ t('chat.groupTabInfo') }}
            </button>
            <button
              v-if="isGroupOwner()"
              type="button"
              :class="{ on: groupTab === 'manage' }"
              @click="groupTab = 'manage'"
            >
              {{ t('chat.groupTabManage') }}
            </button>
          </div>
          <template v-if="groupTab === 'info'">
            <p class="modal-hint">{{ t('chat.groupInviteHowTo') }}</p>
            <p class="modal-name">{{ groupDetail.name }}</p>
            <p v-if="groupDetail.notice" class="modal-section">
              <strong>{{ t('chat.groupNotice') }}</strong><br />
              {{ groupDetail.notice }}
            </p>
            <p class="modal-section">
              <strong>{{ t('chat.groupMembers') }}</strong> ({{ groupDetail.memberCount }})
            </p>
            <ul class="member-chips">
              <li v-for="mem in groupDetail.members" :key="mem.userId">
                {{ mem.nickname }}
                <span v-if="mem.blockedByMe" class="blk">{{ t('chat.blockedByMe') }}</span>
                <span v-if="mem.hasBlockedMe" class="blk">{{ t('chat.blockedMe') }}</span>
                <span v-if="mem.userId === groupDetail.ownerId" class="owner">{{
                  t('chat.groupOwner')
                }}</span>
              </li>
            </ul>
            <button type="button" class="wx-btn-primary leave-btn" @click="leaveCurrentGroup">
              {{ t('chat.leaveGroup') }}
            </button>
          </template>
          <template v-else-if="groupTab === 'manage' && isGroupOwner()">
            <p class="modal-hint">{{ t('chat.groupInviteOwnerTip') }}</p>
            <label class="modal-section">{{ t('chat.groupNameEdit') }}</label>
            <input v-model="groupEditName" class="wx-input full" />
            <label class="modal-section">{{ t('chat.groupNoticeEdit') }}</label>
            <textarea v-model="groupEditNotice" class="wx-input full ta-sm" rows="3" />
            <button type="button" class="wx-btn-primary" @click="saveGroupProfile">{{ t('chat.saveGroup') }}</button>
            <p class="modal-section">
              <button type="button" class="link-btn" @click="genGroupInvite">{{ t('chat.genInvite') }}</button>
            </p>
            <p v-if="inviteResult" class="token-box">{{ inviteResult }}</p>
            <button type="button" class="wx-btn-primary" @click="toggleMuteAll">
              {{ groupDetail.muteAll ? t('chat.unmuteAll') : t('chat.muteAll') }}
            </button>
            <p class="modal-section">{{ t('chat.addMembers') }}</p>
            <ul class="mem-pick">
              <li v-if="!friendsEligibleForGroup.length" class="muted">{{ t('chat.noFriendsToAdd') }}</li>
              <li v-for="f in friendsEligibleForGroup" :key="f.userId">
                <label>
                  <input
                    type="checkbox"
                    :checked="isAddFriendSelected(f.userId)"
                    @change="toggleSelectAddFriend(f.userId)"
                  />
                  {{ f.nickname }} (#{{ f.userId }})
                </label>
              </li>
            </ul>
            <button type="button" class="wx-btn-primary" @click="addGroupMembers">{{ t('chat.add') }}</button>
            <p class="modal-section">{{ t('chat.removeMembers') }}</p>
            <ul class="mem-pick">
              <li v-if="!removableGroupMembers.length" class="muted">{{ t('chat.noRemovableMembers') }}</li>
              <li v-for="m in removableGroupMembers" :key="m.userId">
                <label>
                  <input
                    type="checkbox"
                    :checked="isRemoveMemberSelected(m.userId)"
                    @change="toggleSelectRemoveMember(m.userId)"
                  />
                  {{ m.nickname }} (#{{ m.userId }})
                </label>
              </li>
            </ul>
            <button type="button" class="wx-btn-primary" @click="removeGroupMembers">{{ t('chat.remove') }}</button>
            <p class="modal-section">{{ t('chat.transferOwner') }}</p>
            <input v-model="transferToUid" class="wx-input full" :placeholder="t('tools.userId')" />
            <button type="button" class="wx-btn-primary" @click="transferGroupOwner">{{ t('chat.transfer') }}</button>
            <p class="modal-section">{{ t('chat.muteMember') }}</p>
            <input v-model="muteMemberUid" class="wx-input full" :placeholder="t('tools.userId')" />
            <input v-model="muteUntilStr" type="datetime-local" class="wx-input full" />
            <button type="button" class="wx-btn-primary" @click="muteOneMember">{{ t('chat.applyMute') }}</button>
          </template>
        </div>
      </div>
    </div>

    <div v-if="msgMenuMsg" class="modal-mask" @click.self="msgMenuMsg = null">
      <div class="modal">
        <div class="modal-head">
          <span>{{ t('chat.msgActions') }}</span>
          <button type="button" class="modal-close" @click="msgMenuMsg = null">{{ t('common.close') }}</button>
        </div>
        <div class="modal-body msg-act-sheet">
          <p class="act-hint">{{ t('chat.actionSectionCommon') }}</p>
          <div class="act-row">
            <button type="button" class="act-btn" @click="replyToMessage(msgMenuMsg)">{{ t('chat.reply') }}</button>
            <button
              v-if="msgMenuMsg?.type === 'TEXT' && msgMenuMsg?.content"
              type="button"
              class="act-btn"
              @click="copyMessageText"
            >
              {{ t('chat.copy') }}
            </button>
            <button type="button" class="act-btn" @click="favoriteSelectedMessage">{{ t('chat.favorite') }}</button>
            <button type="button" class="act-btn" @click="pinSelectedMessage">{{ t('chat.pinMsg') }}</button>
            <button v-if="msgMenuMsg?.pinnedByMe" type="button" class="act-btn" @click="unpinSelectedMessage">
              {{ t('chat.unpinMsg') }}
            </button>
            <button type="button" class="act-btn" @click="reactSelectedMessage">{{ t('chat.react') }}</button>
            <button type="button" class="act-btn" @click="forwardOpen = true">{{ t('chat.forward') }}</button>
          </div>
          <p v-if="isSelf(msgMenuMsg) && !msgMenuMsg?.recalled" class="act-hint">{{ t('chat.actionSectionMine') }}</p>
          <div v-if="isSelf(msgMenuMsg)" class="act-row">
            <button
              v-if="!msgMenuMsg?.recalled"
              type="button"
              class="act-btn"
              @click="openReceiptsFromMenu"
            >
              {{ t('chat.viewReceipts') }}
            </button>
            <button v-if="!msgMenuMsg?.recalled" type="button" class="act-btn warn" @click="recallSelectedMessage">
              {{ t('chat.recall') }}
            </button>
            <button
              v-if="msgMenuMsg?.type === 'TEXT' && !msgMenuMsg?.recalled"
              type="button"
              class="act-btn"
              @click="editSelectedMessage"
            >
              {{ t('chat.editMsg') }}
            </button>
          </div>
          <p class="act-hint">{{ t('chat.actionSectionRisk') }}</p>
          <div class="act-row">
            <button type="button" class="act-btn danger" @click="deleteSelfSelectedMessage">
              {{ t('chat.deleteSelf') }}
            </button>
          </div>
          <p class="act-hint">{{ t('chat.actionSectionReport') }}</p>
          <input v-model="reportReason" class="wx-input full" :placeholder="t('chat.reportReason')" />
          <button type="button" class="act-btn full-w" @click="reportSelectedMessage">{{ t('chat.report') }}</button>
        </div>
      </div>
    </div>

    <div v-if="forwardOpen && msgMenuMsg" class="modal-mask" @click.self="forwardOpen = false">
      <div class="modal">
        <div class="modal-head">
          <span>{{ t('chat.forward') }}</span>
          <button type="button" class="modal-close" @click="forwardOpen = false">{{ t('common.close') }}</button>
        </div>
        <div class="modal-body">
          <p class="hint-sm">{{ t('chat.pickTargets') }}</p>
          <ul class="fwd-list">
            <li v-for="c in chat.conversations" :key="c.conversationId">
              <label>
                <input
                  type="checkbox"
                  :checked="forwardTargets.includes(c.conversationId)"
                  @change="toggleForwardTarget(c.conversationId)"
                />
                {{ c.displayName }}
              </label>
            </li>
          </ul>
          <button type="button" class="wx-btn-primary" @click="doForwardMessage">{{ t('common.confirm') }}</button>
        </div>
      </div>
    </div>

    <div v-if="mergeForwardOpen" class="modal-mask" @click.self="mergeForwardOpen = false">
      <div class="modal">
        <div class="modal-head">
          <span>{{ t('chat.mergeForward') }}</span>
          <button type="button" class="modal-close" @click="mergeForwardOpen = false">{{ t('common.close') }}</button>
        </div>
        <div class="modal-body">
          <input v-model="mergeTitle" class="wx-input full" :placeholder="t('chat.mergeTitlePh')" />
          <p class="hint-sm">{{ t('chat.pickTargets') }}</p>
          <ul class="fwd-list">
            <li v-for="c in chat.conversations" :key="c.conversationId">
              <label>
                <input
                  type="checkbox"
                  :checked="forwardTargets.includes(c.conversationId)"
                  @change="toggleForwardTarget(c.conversationId)"
                />
                {{ c.displayName }}
              </label>
            </li>
          </ul>
          <button type="button" class="wx-btn-primary" @click="doMergeForward">{{ t('common.confirm') }}</button>
        </div>
      </div>
    </div>

    <div v-if="batchForwardOpen" class="modal-mask" @click.self="batchForwardOpen = false">
      <div class="modal">
        <div class="modal-head">
          <span>{{ t('chat.batchForward') }}</span>
          <button type="button" class="modal-close" @click="batchForwardOpen = false">{{ t('common.close') }}</button>
        </div>
        <div class="modal-body">
          <p class="hint-sm">{{ t('chat.pickTargets') }}</p>
          <ul class="fwd-list">
            <li v-for="c in chat.conversations" :key="c.conversationId">
              <label>
                <input
                  type="checkbox"
                  :checked="forwardTargets.includes(c.conversationId)"
                  @change="toggleForwardTarget(c.conversationId)"
                />
                {{ c.displayName }}
              </label>
            </li>
          </ul>
          <button type="button" class="wx-btn-primary" @click="doBatchForward">{{ t('common.confirm') }}</button>
        </div>
      </div>
    </div>

    <div v-if="receiptModal && receiptMsg" class="modal-mask" @click.self="receiptModal = false">
      <div class="modal modal-wide">
        <div class="modal-head">
          <span>{{ t('chat.receipts') }}</span>
          <button type="button" class="modal-close" @click="receiptModal = false">{{ t('common.close') }}</button>
        </div>
        <div class="modal-body two-col">
          <div>
            <h4>{{ t('chat.delivered') }}</h4>
            <ul class="rcpt-list">
              <li v-for="d in receiptDelivers" :key="d.userId">{{ d.nickname }} · {{ d.actionAt }}</li>
            </ul>
          </div>
          <div>
            <h4>{{ t('chat.read') }}</h4>
            <ul class="rcpt-list">
              <li v-for="r in receiptReads" :key="r.userId">{{ r.nickname }} · {{ r.actionAt }}</li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <div v-if="mentionPickerOpen" class="modal-mask" @click.self="mentionPickerOpen = false">
      <div class="modal">
        <div class="modal-head">
          <span>@ {{ t('chat.mentionMembers') }}</span>
          <button type="button" class="modal-close" @click="mentionPickerOpen = false">{{ t('common.close') }}</button>
        </div>
        <div class="modal-body">
          <label class="chk-line"
            ><input v-model="mentionAllNext" type="checkbox" @change="mentionPickerOpen = false" />
            {{ t('chat.mentionAll') }}</label
          >
          <ul class="mem-pick">
            <li v-for="mem in mentionMembers" :key="mem.userId">
              <label>
                <input
                  type="checkbox"
                  :checked="mentionUserIds.includes(mem.userId)"
                  @change="toggleMentionMember(mem.userId)"
                />
                {{ mem.nickname }} (#{{ mem.userId }})
              </label>
            </li>
          </ul>
          <button type="button" class="wx-btn-primary" @click="mentionPickerOpen = false">{{ t('common.confirm') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sr {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  border: 0;
}
.layout {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.top {
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--wx-border);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset, 0 4px 18px rgba(19, 152, 127, 0.06);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1cad8f, var(--im-accent));
  box-shadow: 0 0 0 3px rgba(19, 152, 127, 0.2);
}
.title {
  font-weight: 700;
  font-size: 1.02rem;
  letter-spacing: -0.02em;
}
.ws-pill {
  font-size: 0.68rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid var(--wx-border);
  color: var(--wx-sub);
  background: #f5f5f5;
}
.ws-pill.ws-ok {
  color: #1b5e20;
  border-color: #a5d6a7;
  background: #e8f5e9;
}
.ws-pill.ws-bad {
  color: #b71c1c;
  border-color: #ffcdd2;
  background: #ffebee;
}
.tools {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.85rem;
}
.lang {
  padding: 5px 10px;
  border-radius: 10px;
  border: 1px solid var(--wx-border);
  background: #fafcfb;
}
.nick {
  color: var(--wx-sub);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.link {
  color: var(--wx-green);
  font-weight: 600;
}
.body {
  flex: 1;
  display: flex;
  min-height: 0;
}
.rail {
  width: 58px;
  flex-shrink: 0;
  background: var(--wx-rail-bg);
  border-right: none;
  box-shadow: inset -1px 0 0 rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 0;
  gap: 6px;
}
.rail-btn {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  font-size: 1.2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.92);
  transition: background 0.15s ease, transform 0.12s ease;
}
.rail-btn:hover {
  background: rgba(255, 255, 255, 0.22);
}
.rail-btn.on {
  background: rgba(255, 255, 255, 0.28);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.side {
  width: 300px;
  flex-shrink: 0;
  background: var(--wx-sidebar);
  border-right: 1px solid var(--wx-border);
  display: flex;
  flex-direction: column;
  min-width: 0;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.03);
}
.side-head {
  padding: 14px 16px;
  font-weight: 700;
  font-size: 0.82rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--wx-sub);
  border-bottom: 1px solid var(--wx-border);
  background: linear-gradient(180deg, #fafcfb 0%, #fff 100%);
}
.hint {
  padding: 12px;
  color: var(--wx-sub);
  font-size: 0.85rem;
}
.conv-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  flex: 1;
}
.conv-item {
  display: flex;
  gap: 12px;
  padding: 11px 14px;
  cursor: pointer;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  transition: background 0.12s ease;
}
.conv-item:hover {
  background: var(--wx-list-hover);
}
.conv-item.active {
  background: rgba(19, 152, 127, 0.09);
  border-left: 3px solid var(--im-accent);
  padding-left: 11px;
}
.avatar {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: linear-gradient(145deg, #1cad8f, var(--im-accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(19, 152, 127, 0.25);
  overflow: hidden;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}
.avatar.sm {
  width: 36px;
  height: 36px;
  font-size: 0.85rem;
}
.meta {
  flex: 1;
  min-width: 0;
}
.row1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.name-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.g-tag {
  font-size: 0.65rem;
  padding: 1px 5px;
  border-radius: 4px;
  background: #e3f2fd;
  color: #1565c0;
  flex-shrink: 0;
}
.g-tag.sm {
  font-size: 0.7rem;
}
.badge {
  background: var(--wx-danger);
  color: #fff;
  font-size: 0.7rem;
  padding: 1px 6px;
  border-radius: 10px;
  flex-shrink: 0;
}
.preview {
  font-size: 0.8rem;
  color: var(--wx-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--im-chat-bg);
}
.chat-head {
  min-height: 50px;
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--wx-border);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
}
.chat-title {
  font-weight: 600;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag {
  font-size: 0.75rem;
  color: var(--wx-sub);
}
.info-btn {
  font-size: 0.8rem;
  color: var(--wx-green);
  font-weight: 600;
  flex-shrink: 0;
}
.msg-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 14px 18px;
  min-height: 0;
}
.load-row {
  text-align: center;
  margin-bottom: 8px;
}
.load-more {
  font-size: 0.8rem;
  color: var(--wx-green);
  padding: 4px 12px;
}
.empty {
  text-align: center;
  color: var(--wx-sub);
  padding: 40px;
  font-size: 0.9rem;
}
.msg-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  align-items: flex-start;
}
.msg-row.self {
  flex-direction: row-reverse;
}
.bubble-wrap {
  max-width: 70%;
}
.who {
  font-size: 0.7rem;
  color: var(--wx-sub);
  margin-bottom: 2px;
}
.msg-row.self .who {
  text-align: right;
}
.bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 14px 14px 14px 6px;
  font-size: 0.95rem;
  line-height: 1.45;
  word-break: break-word;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}
.msg-row.self .bubble {
  border-radius: 14px 14px 6px 14px;
  background: linear-gradient(165deg, var(--im-bubble-self) 0%, #a8dfd2 100%);
  border-color: var(--im-bubble-self-border);
  box-shadow: 0 2px 12px rgba(19, 152, 127, 0.15);
}
.img {
  max-width: 220px;
  max-height: 200px;
  border-radius: 4px;
  display: block;
}
.vid {
  max-width: 260px;
  max-height: 200px;
  border-radius: 4px;
}
.composer {
  flex-shrink: 0;
  padding: 10px 14px 12px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  border-top: 1px solid var(--wx-border);
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.04);
}
.tool-bar {
  margin-bottom: 6px;
}
.tool {
  font-size: 0.8rem;
  color: var(--wx-green);
  font-weight: 600;
  padding: 4px 8px;
}
.tool:disabled {
  opacity: 0.5;
}
.tool.voice-on {
  color: #c62828;
  animation: pulse-voice 1s ease-in-out infinite;
}
@keyframes pulse-voice {
  50% {
    opacity: 0.65;
  }
}
.composer-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.hidden-file {
  display: none;
}
.ta {
  flex: 1;
  resize: none;
  padding: 10px 12px;
  border: 1px solid var(--wx-border);
  border-radius: 14px;
  min-height: 72px;
  background: #fafcfb;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.ta:focus {
  outline: none;
  border-color: var(--im-accent);
  box-shadow: 0 0 0 3px rgba(19, 152, 127, 0.18);
  background: #fff;
}
.send {
  width: auto;
  padding: 10px 20px;
  flex-shrink: 0;
}
.placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--wx-sub);
  font-size: 0.95rem;
}
.toast {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  padding: 10px 18px;
  border-radius: 8px;
  font-size: 0.85rem;
  z-index: 50;
  max-width: 80%;
  text-align: center;
}
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 40;
  padding: 16px;
}
.modal {
  background: #fff;
  border-radius: 10px;
  max-width: 420px;
  width: 100%;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}
.modal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  font-weight: 600;
}
.modal-close {
  color: var(--wx-green);
  font-size: 0.85rem;
}
.modal-body {
  padding: 14px 16px;
  overflow-y: auto;
}
.modal-name {
  font-size: 1.1rem;
  font-weight: 700;
  margin: 0 0 12px;
}
.modal-hint {
  font-size: 0.82rem;
  color: var(--wx-sub);
  line-height: 1.5;
  margin: 0 0 12px;
  padding: 8px 10px;
  background: #f7f7f7;
  border-radius: 8px;
}
.modal-section {
  font-size: 0.9rem;
  margin: 12px 0;
  line-height: 1.5;
}
.member-chips {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 0.85rem;
}
.member-chips li {
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
}
.owner {
  font-size: 0.7rem;
  color: var(--wx-sub);
  margin-left: 6px;
}
.insearch {
  max-width: 160px;
  padding: 7px 10px;
  border: 1px solid var(--wx-border);
  border-radius: 10px;
  font-size: 0.8rem;
  background: #fafcfb;
}
.info-btn.on {
  background: rgba(19, 152, 127, 0.12);
  color: var(--im-accent);
}
.typing-bar {
  font-size: 0.8rem;
  color: var(--wx-sub);
  padding: 6px 18px;
  background: rgba(255, 255, 255, 0.65);
  border-bottom: 1px solid var(--wx-border);
}
.pin-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #fff8e1;
  border-bottom: 1px solid #ffe082;
  font-size: 0.8rem;
}
.pin-strip-t {
  font-weight: 600;
  color: #f57f17;
}
.pin-chip {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 4px 10px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid #ffcc80;
  font-size: 0.75rem;
}
.reply-bar,
.mention-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(19, 152, 127, 0.08);
  font-size: 0.8rem;
  border-bottom: 1px solid rgba(19, 152, 127, 0.15);
}
.x-sm {
  font-size: 1.2rem;
  line-height: 1;
  padding: 0 6px;
  color: var(--wx-sub);
}
.emoji-pop {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 10px;
  border-bottom: 1px solid var(--wx-border);
  background: rgba(250, 252, 251, 0.95);
}
.em-btn {
  font-size: 1.25rem;
  padding: 4px 6px;
  border-radius: 4px;
}
.em-btn:hover {
  background: #eee;
}
.tool.chk {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
}
.reply-quote {
  font-size: 0.75rem;
  color: var(--wx-sub);
  padding: 4px 8px;
  margin-bottom: 4px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 4px;
  border-left: 3px solid var(--wx-green);
  cursor: pointer;
}
.react-row {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.react-pill {
  font-size: 0.7rem;
  padding: 2px 6px;
  background: #f0f0f0;
  border-radius: 10px;
}
.rcpt {
  display: block;
  margin-top: 4px;
  font-size: 0.65rem;
  color: var(--wx-sub);
  text-align: right;
  background: none;
  border: none;
  cursor: pointer;
  text-decoration: underline;
}
.msg-row.sel-on .bubble {
  outline: 2px solid var(--wx-green);
}
.conv-time {
  font-size: 0.7rem;
  color: var(--wx-sub);
  flex-shrink: 0;
}
.pin-ic {
  margin-right: 2px;
}
.draft-tag {
  color: #f57f17;
  font-weight: 600;
}
.at-all {
  font-size: 0.65rem;
  color: #c62828;
  margin-left: 4px;
}
.aud {
  max-width: 260px;
  height: 36px;
}
.aud.aud-voice {
  min-width: 200px;
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 560px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
.rcpt-list {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 0.8rem;
}
.rcpt-list li {
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
}
.mem-pick {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  max-height: 220px;
  overflow-y: auto;
  font-size: 0.85rem;
}
.mem-pick li {
  padding: 6px 0;
  border-bottom: 1px solid #f5f5f5;
}
.chk-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 0.9rem;
}
.blk {
  font-size: 0.65rem;
  color: #c62828;
  margin-left: 4px;
}
.conv-menu-wrap {
  position: relative;
  flex-shrink: 0;
}
.conv-dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  margin-top: 4px;
  background: #fff;
  border: 1px solid var(--wx-border);
  border-radius: 8px;
  padding: 8px;
  min-width: 200px;
  z-index: 30;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.conv-dropdown button {
  text-align: left;
  font-size: 0.85rem;
  padding: 6px 8px;
  border-radius: 4px;
}
.conv-dropdown .dd-input {
  font-size: 0.8rem;
  padding: 6px 8px;
  border: 1px solid var(--wx-border);
  border-radius: 4px;
}
.dd-danger {
  color: #c62828 !important;
}
.msg-act {
  margin-left: 6px;
  font-size: 0.75rem;
  padding: 0 4px;
  vertical-align: middle;
  opacity: 0.6;
}
.edited-tag {
  font-size: 0.65rem;
  color: var(--wx-sub);
  margin-left: 4px;
}
.modal-wide {
  max-width: 480px;
}
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.tabs button {
  padding: 6px 12px;
  border-radius: 6px;
  border: 1px solid var(--wx-border);
  background: #f5f5f5;
  font-size: 0.85rem;
}
.tabs button.on {
  background: var(--wx-green);
  color: #fff;
  border-color: var(--wx-green);
}
.actions button {
  display: block;
  width: 100%;
  text-align: left;
  padding: 8px 10px;
  margin-bottom: 6px;
  border-radius: 6px;
  border: 1px solid #eee;
  background: #fafafa;
}
.msg-act-sheet .act-hint {
  font-size: 0.72rem;
  color: var(--wx-sub);
  margin: 10px 0 6px;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}
.msg-act-sheet .act-hint:first-child {
  margin-top: 0;
}
.msg-act-sheet .act-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}
.msg-act-sheet .act-btn {
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
  background: #f7f7f7;
  font-size: 0.85rem;
  cursor: pointer;
}
.msg-act-sheet .act-btn.warn {
  border-color: #ffcc80;
  background: #fff8e1;
}
.msg-act-sheet .act-btn.danger {
  border-color: #ffcdd2;
  background: #ffebee;
  color: #c62828;
}
.msg-act-sheet .act-btn.full-w {
  width: 100%;
  margin-top: 6px;
}
.full {
  width: 100%;
  box-sizing: border-box;
}
.ta-sm {
  resize: vertical;
}
.leave-btn {
  margin-top: 12px;
  width: 100%;
}
.token-box {
  font-size: 0.8rem;
  word-break: break-all;
  background: #f5f5f5;
  padding: 8px;
  border-radius: 6px;
}
.link-btn {
  color: var(--wx-green);
  font-weight: 600;
}
.hint-sm {
  font-size: 0.8rem;
  color: var(--wx-sub);
  margin-bottom: 8px;
}
.fwd-list {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  max-height: 200px;
  overflow-y: auto;
  font-size: 0.85rem;
}
.fwd-list li {
  padding: 4px 0;
}
@media (max-width: 720px) {
  .rail {
    flex-direction: row;
    width: 100%;
    height: 48px;
    padding: 0 8px;
    border-right: none;
    border-bottom: 1px solid var(--wx-border);
  }
  .body {
    flex-direction: column;
  }
  .side {
    width: 100%;
    max-height: 40vh;
  }
  .main {
    min-height: 45vh;
  }
}
</style>
