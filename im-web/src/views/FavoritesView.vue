<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import * as msgApi from '@/api/message'
import type { ChatMessageVO, FavoriteMessageVO, SnowflakeId } from '@/types/api'

const { t, locale } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const list = ref<FavoriteMessageVO[]>([])
const kw = ref('')
const cat = ref('')
const loading = ref(false)
const err = ref('')
const editNote = ref<Record<string, string>>({})
const editCat = ref<Record<string, string>>({})
const savingId = ref<string | null>(null)
const imgErr = ref<Record<string, boolean>>({})
const videoThumbErr = ref<Record<string, boolean>>({})
/** 全屏播放：预签名视频 URL */
const playUrl = ref<string | null>(null)

function openVideoPlayer(url: string | null | undefined) {
  if (!url) return
  playUrl.value = url
}

function closeVideoPlayer() {
  playUrl.value = null
}

/** 列表里静音视频：跳到首帧附近作为「封面」 */
function seekVideoThumb(e: Event) {
  const v = e.target as HTMLVideoElement
  try {
    const seek = () => {
      if (!v.duration || Number.isNaN(v.duration)) return
      const t = Math.min(0.35, Math.max(0.05, v.duration * 0.02))
      v.currentTime = t
    }
    seek()
  } catch {
    /* 部分浏览器在无 CORS 时 seek 失败，仍可能显示第 0 帧 */
  }
}

function onVideoThumbErr(mid: string) {
  videoThumbErr.value = { ...videoThumbErr.value, [mid]: true }
}

watch(playUrl, (u) => {
  document.body.style.overflow = u ? 'hidden' : ''
})

function onKeyEscape(e: KeyboardEvent) {
  if (e.key === 'Escape') closeVideoPlayer()
}

function syncEditDrafts() {
  const n: Record<string, string> = {}
  const c: Record<string, string> = {}
  for (const f of list.value) {
    const mid = String(f.messageId)
    n[mid] = f.note ?? ''
    c[mid] = f.categoryName ?? ''
  }
  editNote.value = n
  editCat.value = c
}

function formatTime(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const loc = locale.value === 'en' ? 'en-US' : 'zh-CN'
  return new Intl.DateTimeFormat(loc, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(d)
}

function formatSize(s?: string | number | null): string {
  if (s === null || s === undefined || s === '') return ''
  const n = typeof s === 'string' ? parseInt(s, 10) : s
  if (!Number.isFinite(n) || n < 0) return String(s)
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function typeLabel(tp: string | undefined): string {
  const map: Record<string, string> = {
    TEXT: t('favorites.typeText'),
    IMAGE: t('favorites.typeImage'),
    VIDEO: t('favorites.typeVideo'),
    FILE: t('favorites.typeFile'),
    AUDIO: t('favorites.typeAudio'),
  }
  return map[tp || ''] || t('favorites.typeOther')
}

function textBody(m: ChatMessageVO | undefined): string {
  if (!m || m.recalled) return ''
  return (m.content || '').trim()
}

function isImageMsg(m?: ChatMessageVO) {
  return m?.type === 'IMAGE' && !!m.mediaUrl
}

function isVideoMsg(m?: ChatMessageVO) {
  return m?.type === 'VIDEO'
}

function isFileMsg(m?: ChatMessageVO) {
  return m?.type === 'FILE'
}

function mediaName(m?: ChatMessageVO): string {
  return (m?.mediaMeta?.originalName || '').trim() || ''
}

function markImgErr(mid: string) {
  imgErr.value = { ...imgErr.value, [mid]: true }
}

async function load() {
  loading.value = true
  err.value = ''
  imgErr.value = {}
  videoThumbErr.value = {}
  try {
    list.value = await msgApi.listFavorites(
      kw.value.trim() || undefined,
      cat.value.trim() || undefined
    )
    syncEditDrafts()
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function saveEdit(messageId: SnowflakeId) {
  const mid = String(messageId)
  savingId.value = mid
  err.value = ''
  try {
    await msgApi.updateFavorite({
      messageId,
      note: editNote.value[mid]?.trim() || undefined,
      categoryName: editCat.value[mid]?.trim() || undefined,
    })
    await load()
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  } finally {
    savingId.value = null
  }
}

async function removeFav(messageId: SnowflakeId) {
  try {
    await msgApi.cancelFavorite(messageId)
    await load()
  } catch (e: unknown) {
    err.value = e instanceof Error ? e.message : String(e)
  }
}

function openChat(conversationId: SnowflakeId | undefined) {
  if (!conversationId) return
  router.push({ path: '/', query: { openConv: String(conversationId) } })
}

onMounted(async () => {
  await auth.refreshProfile()
  await load()
  window.addEventListener('keydown', onKeyEscape)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyEscape)
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="page">
    <header class="head">
      <button type="button" class="back" @click="router.push('/')">← {{ t('common.back') }}</button>
      <h1>{{ t('favorites.title') }}</h1>
      <p class="sub">{{ t('favorites.subtitle') }}</p>
    </header>

    <div class="toolbar card">
      <input v-model="kw" class="wx-input" :placeholder="t('favorites.keywordPh')" @keyup.enter="load" />
      <input v-model="cat" class="wx-input" :placeholder="t('favorites.categoryPh')" @keyup.enter="load" />
      <button type="button" class="wx-btn-primary slim" :disabled="loading" @click="load">
        {{ loading ? t('common.loading') : t('favorites.load') }}
      </button>
    </div>

    <p v-if="err" class="err">{{ err }}</p>

    <ul v-if="list.length" class="list">
      <li v-for="f in list" :key="f.favoriteId" class="item card">
        <div class="fav-main">
          <!-- 左侧：图片缩略 / 视频封面或占位 / 文件图标 / 文本 -->
          <div
            v-if="f.message && isImageMsg(f.message) && f.message.mediaUrl"
            class="thumb-box"
          >
            <img
              v-if="!imgErr[String(f.messageId)]"
              :src="f.message.mediaUrl"
              class="thumb img"
              alt=""
              @error="markImgErr(String(f.messageId))"
            />
            <div v-else class="thumb fallback">{{ t('favorites.imageLoadFail') }}</div>
          </div>
          <div v-else-if="f.message && isVideoMsg(f.message)" class="thumb-box video">
            <template v-if="f.message.mediaUrl || f.message.mediaCoverUrl">
              <div
                class="thumb-hit"
                role="button"
                tabindex="0"
                :title="t('favorites.playVideo')"
                @click="openVideoPlayer(f.message!.mediaUrl)"
                @keydown.enter.prevent="openVideoPlayer(f.message!.mediaUrl)"
              >
                <img
                  v-if="f.message.mediaCoverUrl"
                  :src="f.message.mediaCoverUrl"
                  class="thumb img"
                  alt=""
                />
                <video
                  v-else-if="f.message.mediaUrl && !videoThumbErr[String(f.messageId)]"
                  :src="f.message.mediaUrl"
                  class="thumb vid-el"
                  muted
                  playsinline
                  preload="metadata"
                  @loadedmetadata="seekVideoThumb"
                  @error="onVideoThumbErr(String(f.messageId))"
                />
                <div v-else class="thumb vid-ph sm-fallback">
                  <span class="play-icon">▶</span>
                </div>
                <div class="play-overlay" aria-hidden="true">
                  <span class="play-icon-lg">▶</span>
                </div>
              </div>
            </template>
            <div v-else class="thumb vid-ph">
              <span class="play-icon">▶</span>
            </div>
          </div>
          <div v-else-if="f.message && isFileMsg(f.message)" class="thumb-box file">
            <div class="thumb file-ic" aria-hidden="true">📎</div>
          </div>
          <div v-else class="thumb-box text">
            <div class="thumb text-ph" aria-hidden="true">💬</div>
          </div>

          <div class="fav-body">
            <div class="row-type">
              <span class="pill">{{ typeLabel(f.message?.type) }}</span>
              <span class="who">{{ f.message?.senderNickname || '—' }}</span>
            </div>
            <div class="row-time muted">
              <span>{{ t('favorites.msgTime', { time: formatTime(f.message?.createdAt) }) }}</span>
              <span class="dot-sep">·</span>
              <span>{{ t('favorites.at') }} {{ formatTime(f.favoriteAt) }}</span>
            </div>

            <template v-if="f.message?.recalled">
              <p class="recalled">{{ t('chat.recalled') }}</p>
            </template>
            <template v-else-if="isVideoMsg(f.message)">
              <p class="content-title">
                {{ mediaName(f.message) || t('chat.videoMsg') }}
                <template v-if="formatSize(f.message?.mediaMeta?.size)">
                  <span class="size">{{ formatSize(f.message?.mediaMeta?.size) }}</span>
                </template>
              </p>
              <button
                v-if="f.message.mediaUrl"
                type="button"
                class="play-inline"
                @click="openVideoPlayer(f.message.mediaUrl)"
              >
                {{ t('favorites.playVideo') }}
              </button>
            </template>
            <template v-else-if="isFileMsg(f.message)">
              <p class="content-title">
                {{ mediaName(f.message) || f.message?.content || '—' }}
                <template v-if="formatSize(f.message?.mediaMeta?.size)">
                  <span class="size">{{ formatSize(f.message?.mediaMeta?.size) }}</span>
                </template>
              </p>
            </template>
            <template v-else-if="textBody(f.message)">
              <p class="text-preview">{{ textBody(f.message) }}</p>
            </template>
            <template v-else-if="isImageMsg(f.message)">
              <p v-if="textBody(f.message)" class="text-preview caption">{{ textBody(f.message) }}</p>
              <p v-else class="muted sm">{{ t('chat.sendImage') }}</p>
            </template>
            <template v-else>
              <p class="muted sm">{{ t('favorites.noContent') }}</p>
            </template>
          </div>
        </div>

        <div v-if="f.note || f.categoryName" class="tag-row">
          <span v-if="f.categoryName" class="tag">{{ f.categoryName }}</span>
          <span v-if="f.note" class="note-line">{{ f.note }}</span>
        </div>

        <details class="meta-edit">
          <summary>{{ t('favorites.editMeta') }}</summary>
          <div class="edit-inner">
            <label class="edit-label">
              <span>{{ t('favorites.notePh') }}</span>
              <input v-model="editNote[String(f.messageId)]" class="wx-input edit-input" type="text" />
            </label>
            <label class="edit-label">
              <span>{{ t('favorites.categoryEditPh') }}</span>
              <input v-model="editCat[String(f.messageId)]" class="wx-input edit-input" type="text" />
            </label>
            <button
              type="button"
              class="save-btn"
              :disabled="savingId === String(f.messageId)"
              @click="saveEdit(f.messageId)"
            >
              {{ savingId === String(f.messageId) ? t('common.loading') : t('favorites.saveEdit') }}
            </button>
          </div>
        </details>

        <div class="actions">
          <button type="button" class="link-btn" @click="openChat(f.message?.conversationId)">
            {{ t('favorites.openChat') }}
          </button>
          <button type="button" class="link-btn danger" @click="removeFav(f.messageId)">
            {{ t('favorites.remove') }}
          </button>
        </div>
      </li>
    </ul>
    <p v-else-if="!loading" class="empty muted">{{ t('favorites.empty') }}</p>

    <Teleport to="body">
      <div
        v-if="playUrl"
        class="v-modal-mask"
        role="dialog"
        aria-modal="true"
        @click.self="closeVideoPlayer"
      >
        <div class="v-modal">
          <button type="button" class="v-close" :aria-label="t('common.close')" @click="closeVideoPlayer">
            ×
          </button>
          <video :src="playUrl" controls playsinline class="v-full" />
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: var(--wx-bg);
  padding: 16px 16px 48px;
  max-width: 560px;
  margin: 0 auto;
}
.head {
  margin-bottom: 16px;
}
.head h1 {
  font-size: 1.25rem;
  margin: 8px 0 4px;
}
.sub {
  margin: 0;
  font-size: 0.85rem;
  color: var(--wx-sub);
}
.back {
  color: var(--wx-green);
  font-weight: 600;
  padding: 0;
}
.toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}
.toolbar .wx-input {
  margin: 0;
}
.slim {
  width: auto;
  align-self: flex-start;
}
.card {
  background: var(--wx-white);
  border: 1px solid var(--wx-border);
  border-radius: 12px;
  padding: 0;
  overflow: hidden;
  box-shadow: var(--wx-card-shadow, 0 4px 20px rgba(0, 0, 0, 0.06));
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.item.card {
  padding: 12px 14px 14px;
}
.fav-main {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.thumb-box {
  flex: 0 0 72px;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  background: #f0f4f3;
}
.thumb.img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb.fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.65rem;
  color: var(--wx-sub);
  padding: 4px;
  text-align: center;
}
.thumb-hit {
  position: relative;
  width: 100%;
  height: 100%;
  cursor: pointer;
  border-radius: 8px;
  overflow: hidden;
}
.thumb-hit:focus {
  outline: 2px solid var(--im-accent, #13987f);
  outline-offset: 2px;
}
.thumb-hit .thumb.img,
.thumb-hit .vid-el {
  pointer-events: none;
}
.vid-el {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  background: #1a1a1a;
}
.play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.28);
  pointer-events: none;
}
.play-icon-lg {
  font-size: 1.5rem;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.45);
  margin-left: 3px;
}
.vid-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, #2d3e3c, #1a2624);
}
.vid-ph.sm-fallback {
  border-radius: 8px;
}
.play-icon {
  font-size: 1.4rem;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}
.play-inline {
  margin: 8px 0 0;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid var(--im-accent, #13987f);
  background: rgba(19, 152, 127, 0.1);
  color: var(--im-accent, #13987f);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
}
.play-inline:hover {
  background: rgba(19, 152, 127, 0.18);
}
.v-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}
.v-modal {
  position: relative;
  width: 100%;
  max-width: min(720px, 100vw - 32px);
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
}
.v-close {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 1.4rem;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: none;
}
.v-close:hover {
  background: rgba(255, 255, 255, 0.2);
}
.v-full {
  width: 100%;
  max-height: min(80vh, 720px);
  display: block;
  vertical-align: top;
}
.thumb.file-ic,
.thumb.text-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.75rem;
  background: linear-gradient(160deg, #e8f0ee, #dfe8e5);
}
.fav-body {
  flex: 1;
  min-width: 0;
}
.row-type {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.pill {
  font-size: 0.7rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(19, 152, 127, 0.12);
  color: var(--im-accent, #13987f);
}
.who {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--wx-text);
}
.row-time {
  font-size: 0.72rem;
  margin-bottom: 8px;
}
.dot-sep {
  margin: 0 4px;
  opacity: 0.6;
}
.muted {
  color: var(--wx-sub);
}
.recalled {
  margin: 0;
  font-size: 0.88rem;
  color: var(--wx-sub);
  font-style: italic;
}
.content-title {
  margin: 0;
  font-size: 0.88rem;
  line-height: 1.45;
  color: var(--wx-text);
  word-break: break-word;
}
.content-title .size {
  color: var(--wx-sub);
  font-weight: 500;
}
.content-title .size::before {
  content: ' · ';
}
.text-preview {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--wx-text);
  white-space: pre-wrap;
  word-break: break-word;
}
.text-preview.caption {
  margin-top: 6px;
  font-size: 0.85rem;
  color: var(--wx-sub);
}
.sm {
  font-size: 0.82rem;
  margin: 0;
}
.tag-row {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  font-size: 0.78rem;
}
.tag {
  background: rgba(19, 152, 127, 0.12);
  color: var(--im-accent, #13987f);
  padding: 2px 8px;
  border-radius: 999px;
}
.note-line {
  color: var(--wx-sub);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta-edit {
  margin-top: 8px;
  font-size: 0.85rem;
}
.meta-edit summary {
  cursor: pointer;
  color: var(--wx-green);
  font-weight: 600;
  padding: 4px 0;
  list-style: none;
}
.meta-edit summary::-webkit-details-marker {
  display: none;
}
.edit-inner {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.edit-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.75rem;
  color: var(--wx-sub);
}
.edit-input {
  margin: 0;
  font-size: 0.88rem;
}
.save-btn {
  align-self: flex-start;
  padding: 6px 14px;
  border-radius: 8px;
  background: var(--wx-green);
  color: #fff;
  font-size: 0.85rem;
  font-weight: 600;
}
.save-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.actions {
  margin-top: 10px;
  display: flex;
  gap: 12px;
}
.link-btn {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--wx-green);
  padding: 0;
}
.link-btn.danger {
  color: var(--wx-danger);
}
.err {
  color: var(--wx-danger);
  font-size: 0.85rem;
}
.empty {
  text-align: center;
  padding: 32px 16px;
}
</style>
