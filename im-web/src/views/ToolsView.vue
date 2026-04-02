<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import * as userApi from '@/api/user'
import * as convApi from '@/api/conversation'
import * as friendApi from '@/api/friend'
import * as msgApi from '@/api/message'
import * as stickerApi from '@/api/stickers'
import type {
  ChatMessageVO,
  ConversationListVO,
  ConversationUnreadVO,
  SnowflakeId,
  StickerPackDetailVO,
  UserSimpleVO,
} from '@/types/api'
import { normalizeSnowflakeIds } from '@/utils/ids'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const toast = ref('')
function showToast(m: string) {
  toast.value = m
  setTimeout(() => {
    toast.value = ''
  }, 2800)
}

const blacklist = ref<UserSimpleVO[]>([])
const blUid = ref('')

const archived = ref<ConversationListVO[]>([])
const hidden = ref<ConversationListVO[]>([])

const inviteToken = ref('')

const groupName = ref('')
const groupPickFriends = ref<UserSimpleVO[]>([])
const groupSelectedFriendIds = ref<SnowflakeId[]>([])

const searchKw = ref('')
const searchCid = ref('')
const searchHits = ref<ChatMessageVO[]>([])
const searchHasMore = ref(false)
const searchNextBefore = ref<SnowflakeId | null>(null)

const packs = ref<StickerPackDetailVO[]>([])

const pushPlatform = ref('web')
const pushToken = ref('')

const tagName = ref('')
const tags = ref<Awaited<ReturnType<typeof friendApi.listFriendTags>>>([])
const assignFriend = ref('')
const assignTagIds = ref('')

const unreadList = ref<ConversationUnreadVO[]>([])

const adminPackCode = ref('')
const adminPackName = ref('')
const adminItemPackId = ref('')
const adminItemCode = ref('')
const adminItemUrl = ref('')

async function loadBlacklist() {
  try {
    blacklist.value = await userApi.listBlacklist()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function addBl() {
  const id = blUid.value.trim()
  if (!id) return
  try {
    await userApi.addBlacklist(id)
    blUid.value = ''
    await loadBlacklist()
    showToast(t('tools.ok'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function rmBl(uid: SnowflakeId) {
  try {
    await userApi.removeBlacklist(uid)
    await loadBlacklist()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function loadArchivedHidden() {
  try {
    const [a, h] = await Promise.all([
      convApi.fetchArchivedConversations(),
      convApi.fetchHiddenConversations(),
    ])
    archived.value = a
    hidden.value = h
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function restore(id: SnowflakeId) {
  try {
    await convApi.restoreConversation(id)
    await loadArchivedHidden()
    showToast(t('tools.restored'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function joinInvite() {
  try {
    await convApi.joinGroupByInvite({ token: inviteToken.value.trim() })
    showToast(t('tools.joinOk'))
    inviteToken.value = ''
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

function toggleGroupFriendPick(uid: SnowflakeId) {
  const s = String(uid)
  const i = groupSelectedFriendIds.value.findIndex((x) => String(x) === s)
  if (i >= 0) groupSelectedFriendIds.value.splice(i, 1)
  else groupSelectedFriendIds.value.push(uid)
}

function isGroupFriendPicked(uid: SnowflakeId) {
  return groupSelectedFriendIds.value.some((x) => String(x) === String(uid))
}

async function loadFriendsForGroupPick() {
  try {
    groupPickFriends.value = await friendApi.listFriends()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
    groupPickFriends.value = []
  }
}

async function createGroup() {
  const ids = normalizeSnowflakeIds(groupSelectedFriendIds.value)
  if (!groupName.value.trim() || !ids.length) {
    showToast(t('tools.fillGroup'))
    return
  }
  try {
    await convApi.createGroup({ name: groupName.value.trim(), memberIds: ids })
    showToast(t('tools.groupCreated'))
    groupName.value = ''
    groupSelectedFriendIds.value = []
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function doSearch() {
  if (!searchKw.value.trim()) return
  try {
    const cid = searchCid.value.trim() || undefined
    const page = await msgApi.searchMessages(searchKw.value.trim(), {
      conversationId: cid,
      size: 40,
    })
    searchHits.value = page.items
    searchHasMore.value = page.hasMore
    searchNextBefore.value = page.nextBeforeMessageId
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function loadMoreSearch() {
  if (!searchHasMore.value || searchNextBefore.value == null || !searchKw.value.trim()) return
  try {
    const cid = searchCid.value.trim() || undefined
    const page = await msgApi.searchMessages(searchKw.value.trim(), {
      conversationId: cid,
      beforeMessageId: searchNextBefore.value,
      size: 40,
    })
    searchHits.value.push(...page.items)
    searchHasMore.value = page.hasMore
    searchNextBefore.value = page.nextBeforeMessageId
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function loadStickers() {
  try {
    packs.value = await stickerApi.listStickerPacks()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function registerPush() {
  try {
    await userApi.registerPushToken({
      platform: pushPlatform.value.trim(),
      deviceToken: pushToken.value.trim(),
    })
    showToast(t('tools.pushOk'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function loadTags() {
  try {
    tags.value = await friendApi.listFriendTags()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function createTag() {
  if (!tagName.value.trim()) return
  try {
    await friendApi.createFriendTag({ name: tagName.value.trim() })
    tagName.value = ''
    await loadTags()
    showToast(t('tools.ok'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function delTag(id: SnowflakeId) {
  try {
    await friendApi.deleteFriendTag(id)
    await loadTags()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function assignTags() {
  const fid = assignFriend.value.trim()
  const tids = assignTagIds.value
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
  if (!fid || !tids.length) return
  try {
    await friendApi.assignFriendTags({ friendUserId: fid, tagIds: tids })
    showToast(t('tools.ok'))
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function loadUnread() {
  try {
    unreadList.value = await convApi.fetchUnreadSummary()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function adminCreatePack() {
  try {
    await stickerApi.createStickerPack({
      code: adminPackCode.value.trim(),
      name: adminPackName.value.trim(),
    })
    showToast(t('tools.ok'))
    await loadStickers()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

async function adminCreateItem() {
  const pid = adminItemPackId.value.trim()
  if (!pid) return
  try {
    await stickerApi.createStickerItem({
      packId: pid,
      code: adminItemCode.value.trim(),
      imageUrl: adminItemUrl.value.trim(),
    })
    showToast(t('tools.ok'))
    await loadStickers()
  } catch (e: unknown) {
    showToast(e instanceof Error ? e.message : String(e))
  }
}

onMounted(async () => {
  await auth.refreshProfile()
  void loadBlacklist()
  void loadArchivedHidden()
  void loadStickers()
  void loadTags()
  void loadUnread()
  void loadFriendsForGroupPick()
})
</script>

<template>
  <div class="page">
    <header class="head">
      <button type="button" class="back" @click="router.push('/')">← {{ t('common.back') }}</button>
      <h1>{{ t('tools.title') }}</h1>
      <router-link v-if="auth.isAdmin" to="/admin/reports" class="admin-link">{{
        t('tools.adminReports')
      }}</router-link>
    </header>

    <div class="sections">
      <section class="card">
        <h2>{{ t('tools.unread') }}</h2>
        <button type="button" class="mini" @click="loadUnread">{{ t('common.retry') }}</button>
        <ul class="simple">
          <li v-for="u in unreadList" :key="u.conversationId">
            #{{ u.conversationId }} — {{ u.unreadCount }}
          </li>
          <li v-if="!unreadList.length" class="muted">{{ t('tools.empty') }}</li>
        </ul>
      </section>

      <section class="card">
        <h2>{{ t('tools.blacklist') }}</h2>
        <div class="row">
          <input v-model="blUid" class="wx-input" :placeholder="t('tools.userId')" />
          <button type="button" class="wx-btn-primary slim" @click="addBl">{{ t('tools.add') }}</button>
        </div>
        <ul class="simple">
          <li v-for="b in blacklist" :key="b.userId" class="row between">
            <span>{{ b.nickname }} (#{{ b.userId }})</span>
            <button type="button" class="mini" @click="rmBl(b.userId)">{{ t('tools.remove') }}</button>
          </li>
        </ul>
      </section>

      <section class="card">
        <h2>{{ t('tools.archived') }}</h2>
        <button type="button" class="mini" @click="loadArchivedHidden">{{ t('common.retry') }}</button>
        <ul class="simple">
          <li v-for="c in archived" :key="c.conversationId" class="row between">
            <span>{{ c.displayName }}</span>
            <button type="button" class="mini" @click="restore(c.conversationId)">
              {{ t('tools.restore') }}
            </button>
          </li>
          <li v-if="!archived.length" class="muted">{{ t('tools.empty') }}</li>
        </ul>
      </section>

      <section class="card">
        <h2>{{ t('tools.hidden') }}</h2>
        <ul class="simple">
          <li v-for="c in hidden" :key="c.conversationId" class="row between">
            <span>{{ c.displayName }}</span>
            <button type="button" class="mini" @click="restore(c.conversationId)">
              {{ t('tools.restore') }}
            </button>
          </li>
          <li v-if="!hidden.length" class="muted">{{ t('tools.empty') }}</li>
        </ul>
      </section>

      <section class="card">
        <h2>{{ t('tools.joinInvite') }}</h2>
        <input v-model="inviteToken" class="wx-input" :placeholder="t('tools.inviteToken')" />
        <button type="button" class="wx-btn-primary" @click="joinInvite">{{ t('tools.join') }}</button>
      </section>

      <section class="card">
        <h2>{{ t('tools.createGroup') }}</h2>
        <p class="muted" style="margin: 0 0 8px; font-size: 12px">{{ t('tools.pickFriendsForGroup') }}</p>
        <input v-model="groupName" class="wx-input" :placeholder="t('tools.groupName')" />
        <button type="button" class="mini" style="margin: 8px 0" @click="loadFriendsForGroupPick">
          {{ t('common.retry') }} · {{ t('contacts.friends') }}
        </button>
        <ul class="group-friend-pick">
          <li v-if="!groupPickFriends.length" class="muted">{{ t('tools.empty') }}</li>
          <li v-for="f in groupPickFriends" :key="f.userId">
            <label>
              <input
                type="checkbox"
                :checked="isGroupFriendPicked(f.userId)"
                @change="toggleGroupFriendPick(f.userId)"
              />
              {{ f.nickname }} (#{{ f.userId }})
            </label>
          </li>
        </ul>
        <button type="button" class="wx-btn-primary" @click="createGroup">{{ t('tools.create') }}</button>
      </section>

      <section class="card">
        <h2>{{ t('tools.msgSearch') }}</h2>
        <input v-model="searchKw" class="wx-input" :placeholder="t('tools.keyword')" />
        <input v-model="searchCid" class="wx-input" :placeholder="t('tools.convIdOptional')" />
        <button type="button" class="wx-btn-primary" @click="doSearch">{{ t('contacts.searchBtn') }}</button>
        <p class="muted" style="margin-top: 8px; font-size: 12px">多个词用空格分隔，同时包含（AND）</p>
        <ul class="hits">
          <li v-for="m in searchHits" :key="m.id">
            <span class="muted">#{{ m.conversationId }}</span> {{ m.content?.slice(0, 120) }}
          </li>
        </ul>
        <button
          v-if="searchHasMore"
          type="button"
          class="wx-btn-primary slim"
          style="margin-top: 8px"
          @click="loadMoreSearch"
        >
          加载更多
        </button>
      </section>

      <section class="card fav-teaser">
        <h2>{{ t('tools.favorites') }}</h2>
        <p class="muted">{{ t('favorites.toolsHint') }}</p>
        <RouterLink to="/favorites" class="fav-link">{{ t('favorites.goPage') }}</RouterLink>
      </section>

      <section class="card">
        <h2>{{ t('tools.friendTags') }}</h2>
        <div class="row">
          <input v-model="tagName" class="wx-input" :placeholder="t('tools.tagName')" />
          <button type="button" class="wx-btn-primary slim" @click="createTag">{{ t('tools.create') }}</button>
        </div>
        <ul class="simple">
          <li v-for="g in tags" :key="g.tagId" class="row between">
            <span>{{ g.name }} ({{ g.memberCount }})</span>
            <button type="button" class="mini" @click="delTag(g.tagId)">{{ t('tools.remove') }}</button>
          </li>
        </ul>
        <div class="row">
          <input v-model="assignFriend" class="wx-input" :placeholder="t('tools.friendUserId')" />
          <input v-model="assignTagIds" class="wx-input" :placeholder="t('tools.tagIdsCsv')" />
        </div>
        <button type="button" class="wx-btn-primary" @click="assignTags">{{ t('tools.assignTags') }}</button>
      </section>

      <section class="card">
        <h2>{{ t('tools.pushToken') }}</h2>
        <input v-model="pushPlatform" class="wx-input" placeholder="platform" />
        <input v-model="pushToken" class="wx-input" placeholder="deviceToken" />
        <button type="button" class="wx-btn-primary" @click="registerPush">{{ t('tools.register') }}</button>
      </section>

      <section class="card">
        <h2>{{ t('tools.stickers') }}</h2>
        <button type="button" class="mini" @click="loadStickers">{{ t('common.retry') }}</button>
        <div v-for="p in packs" :key="p.packId" class="pack">
          <div class="pack-name">{{ p.name }}</div>
          <div class="grid">
            <img
              v-for="it in p.items || []"
              :key="it.itemId"
              :src="it.imageUrl || ''"
              class="st"
              alt=""
            />
          </div>
        </div>
      </section>

      <section v-if="auth.isAdmin" class="card admin">
        <h2>{{ t('tools.stickerAdmin') }}</h2>
        <input v-model="adminPackCode" class="wx-input" placeholder="pack code" />
        <input v-model="adminPackName" class="wx-input" placeholder="pack name" />
        <button type="button" class="wx-btn-primary" @click="adminCreatePack">{{ t('tools.createPack') }}</button>
        <input v-model="adminItemPackId" class="wx-input" placeholder="packId" />
        <input v-model="adminItemCode" class="wx-input" placeholder="item code" />
        <input v-model="adminItemUrl" class="wx-input" placeholder="imageUrl" />
        <button type="button" class="wx-btn-primary" @click="adminCreateItem">{{ t('tools.createItem') }}</button>
      </section>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: var(--wx-bg);
  padding: 16px;
  max-width: 640px;
  margin: 0 auto;
  padding-bottom: 48px;
}
.head {
  margin-bottom: 16px;
}
.head h1 {
  font-size: 1.2rem;
  margin: 8px 0;
}
.back {
  color: var(--wx-green);
  font-weight: 600;
}
.admin-link {
  display: inline-block;
  margin-top: 6px;
  color: #c62828;
  font-size: 0.9rem;
}
.sections {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.card {
  background: var(--wx-white);
  border: 1px solid var(--wx-border);
  border-radius: 10px;
  padding: 12px;
}
.card h2 {
  font-size: 0.95rem;
  margin: 0 0 10px;
}
.row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.row.between {
  justify-content: space-between;
}
.wx-input {
  flex: 1;
  min-width: 120px;
  margin-bottom: 6px;
}
.slim {
  width: auto !important;
  flex: none;
}
.mini {
  font-size: 0.75rem;
  padding: 4px 10px;
  margin-bottom: 8px;
}
.simple {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 0.85rem;
}
.muted {
  color: var(--wx-sub);
}
.hits {
  margin: 8px 0 0;
  padding-left: 16px;
  font-size: 0.8rem;
}
.pack {
  margin-top: 10px;
}
.pack-name {
  font-weight: 600;
  margin-bottom: 6px;
}
.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.st {
  width: 48px;
  height: 48px;
  object-fit: contain;
  border: 1px solid #eee;
  border-radius: 4px;
}
.fav-teaser .fav-link {
  display: inline-block;
  margin-top: 10px;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 8px;
  background: var(--wx-green);
  color: #fff !important;
}
.group-friend-pick {
  list-style: none;
  margin: 0 0 10px;
  padding: 0;
  max-height: 200px;
  overflow-y: auto;
  font-size: 0.85rem;
}
.group-friend-pick li {
  padding: 6px 0;
  border-bottom: 1px solid #f0f0f0;
}
.toast {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.82);
  color: #fff;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
  z-index: 100;
}
</style>
