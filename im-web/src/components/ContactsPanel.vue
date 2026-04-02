<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useContactsStore } from '@/stores/contacts'
import type { SnowflakeId } from '@/types/api'

const { t } = useI18n()
const auth = useAuthStore()
const contacts = useContactsStore()

const kw = ref('')

const emit = defineEmits<{
  openChat: [userId: SnowflakeId]
}>()

function initial(name: string) {
  return (name || '?').trim().slice(0, 1).toUpperCase()
}

async function doSearch() {
  await contacts.search(kw.value)
}

function filteredSearch() {
  const me = auth.user?.id
  return contacts.searchResults.filter((u) => u.userId !== me)
}

onMounted(() => {
  void contacts.loadFriendTags()
})

function onTagFilter(e: Event) {
  const v = (e.target as HTMLSelectElement).value
  contacts.friendListTagId = v === '' ? undefined : v
  void contacts.loadFriendsAndRequests()
}
</script>

<template>
  <div class="contacts">
    <div class="block">
      <div class="block-title">{{ t('contacts.requests') }}</div>
      <div v-if="contacts.loading" class="hint">{{ t('common.loading') }}</div>
      <div v-else-if="!contacts.requests.length" class="hint">{{ t('contacts.noRequests') }}</div>
      <ul v-else class="list">
        <li v-for="r in contacts.requests" :key="r.id" class="row">
          <div class="avatar">{{ initial(contacts.nicknameForUserId(r.fromUserId)) }}</div>
          <div class="grow">
            <div class="name">{{ contacts.nicknameForUserId(r.fromUserId) }}</div>
            <div v-if="r.remark" class="sub">{{ r.remark }}</div>
          </div>
          <button type="button" class="btn ok" @click="contacts.accept(r.id)">
            {{ t('contacts.accept') }}
          </button>
          <button type="button" class="btn" @click="contacts.reject(r.id)">
            {{ t('contacts.reject') }}
          </button>
        </li>
      </ul>
    </div>

    <div class="block">
      <div class="block-title">{{ t('contacts.search') }}</div>
      <div class="search-row">
        <input v-model="kw" class="wx-input" :placeholder="t('chat.searchPlaceholder')" @keyup.enter="doSearch" />
        <button type="button" class="wx-btn-primary slim" @click="doSearch">
          {{ t('contacts.searchBtn') }}
        </button>
      </div>
      <ul v-if="contacts.searchLoading" class="hint">{{ t('common.loading') }}</ul>
      <ul v-else-if="filteredSearch().length" class="list">
        <li v-for="u in filteredSearch()" :key="u.userId" class="row">
          <div class="avatar">{{ initial(u.nickname) }}</div>
          <div class="grow">
            <div class="name">{{ u.nickname }}</div>
            <div class="sub">{{ u.phone || u.email || '' }}</div>
          </div>
          <template v-if="u.friend">
            <button type="button" class="btn primary" @click="emit('openChat', u.userId)">
              {{ t('contacts.openChat') }}
            </button>
          </template>
          <button v-else type="button" class="btn" @click="contacts.addFriend(u.userId)">
            {{ t('contacts.addFriend') }}
          </button>
        </li>
      </ul>
    </div>

    <div class="block">
      <div class="block-title">{{ t('contacts.friends') }}</div>
      <div class="tag-filter">
        <label class="tag-lab">{{ t('contacts.filterByTag') }}</label>
        <select
          class="wx-input tag-sel"
          :value="contacts.friendListTagId ?? ''"
          @change="onTagFilter"
        >
          <option value="">{{ t('contacts.allFriends') }}</option>
          <option v-for="g in contacts.friendTags" :key="g.tagId" :value="String(g.tagId)">
            {{ g.name }}
          </option>
        </select>
      </div>
      <div v-if="contacts.loading" class="hint">{{ t('common.loading') }}</div>
      <div v-else-if="!contacts.friends.length" class="hint">{{ t('contacts.noFriends') }}</div>
      <ul v-else class="list">
        <li
          v-for="f in contacts.friends"
          :key="f.userId"
          class="row clickable"
          @click="emit('openChat', f.userId)"
        >
          <div class="avatar">{{ initial(f.aliasName || f.nickname) }}</div>
          <div class="grow">
            <div class="name">{{ f.aliasName || f.nickname }}</div>
            <div class="sub">{{ f.phone || f.email || '' }}</div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.contacts {
  flex: 1;
  overflow-y: auto;
  padding: 0 0 12px;
}
.block {
  border-bottom: 1px solid #eee;
  padding: 10px 12px 14px;
}
.block-title {
  font-weight: 600;
  font-size: 0.85rem;
  margin-bottom: 10px;
  color: var(--wx-sub);
}
.hint {
  font-size: 0.85rem;
  color: var(--wx-sub);
  padding: 8px 0;
}
.search-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.search-row .wx-input {
  flex: 1;
}
.slim {
  width: auto !important;
  padding: 10px 14px !important;
  flex-shrink: 0;
}
.list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.row.clickable {
  cursor: pointer;
}
.row.clickable:hover {
  background: var(--wx-list-hover);
  margin: 0 -8px;
  padding-left: 8px;
  padding-right: 8px;
  border-radius: 6px;
}
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(145deg, #1cad8f, var(--im-accent, #13987f));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
  font-size: 0.9rem;
  box-shadow: 0 2px 8px rgba(19, 152, 127, 0.22);
}
.grow {
  flex: 1;
  min-width: 0;
}
.name {
  font-weight: 600;
  font-size: 0.9rem;
}
.sub {
  font-size: 0.75rem;
  color: var(--wx-sub);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.btn {
  font-size: 0.75rem;
  padding: 6px 10px;
  border-radius: 4px;
  border: 1px solid var(--wx-border);
  background: #fff;
  flex-shrink: 0;
}
.btn.ok {
  background: var(--wx-green);
  color: #fff;
  border-color: var(--wx-green);
}
.btn.primary {
  color: var(--wx-green);
  border-color: var(--wx-green);
  font-weight: 600;
}
.tag-filter {
  margin-bottom: 10px;
}
.tag-lab {
  display: block;
  font-size: 0.75rem;
  color: var(--wx-sub);
  margin-bottom: 4px;
}
.tag-sel {
  width: 100%;
  margin-bottom: 0 !important;
}
</style>
