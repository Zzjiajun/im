import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as friendApi from '@/api/friend'
import * as userApi from '@/api/user'
import type {
  FriendRequest,
  FriendTagVO,
  SnowflakeId,
  UserSearchVO,
  UserSimpleVO,
} from '@/types/api'

export const useContactsStore = defineStore('contacts', () => {
  const friends = ref<UserSimpleVO[]>([])
  const requests = ref<FriendRequest[]>([])
  const searchResults = ref<UserSearchVO[]>([])
  const lastSearchKeyword = ref('')
  const loading = ref(false)
  const searchLoading = ref(false)
  const error = ref('')
  const friendTags = ref<FriendTagVO[]>([])
  const friendListTagId = ref<SnowflakeId | undefined>(undefined)

  async function loadFriendTags() {
    try {
      friendTags.value = await friendApi.listFriendTags()
    } catch {
      friendTags.value = []
    }
  }

  async function loadFriendsAndRequests() {
    loading.value = true
    error.value = ''
    try {
      const [f, r] = await Promise.all([
        friendApi.listFriends(friendListTagId.value),
        friendApi.listFriendRequests(),
      ])
      friends.value = f
      requests.value = (r || []).filter((x) => x.status === 'PENDING')
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : String(e)
    } finally {
      loading.value = false
    }
  }

  async function search(keyword: string) {
    lastSearchKeyword.value = keyword.trim()
    if (!lastSearchKeyword.value) {
      searchResults.value = []
      return
    }
    searchLoading.value = true
    try {
      searchResults.value = await userApi.searchUsers(lastSearchKeyword.value)
    } finally {
      searchLoading.value = false
    }
  }

  async function addFriend(toUserId: SnowflakeId) {
    await friendApi.sendFriendRequest(toUserId)
    await loadFriendsAndRequests()
    if (lastSearchKeyword.value) {
      await search(lastSearchKeyword.value)
    }
  }

  async function accept(requestId: SnowflakeId) {
    await friendApi.handleFriendRequest(requestId, true)
    await loadFriendsAndRequests()
  }

  async function reject(requestId: SnowflakeId) {
    await friendApi.handleFriendRequest(requestId, false)
    await loadFriendsAndRequests()
  }

  function nicknameForUserId(userId: SnowflakeId): string {
    const f = friends.value.find((x) => x.userId === userId)
    if (f) return f.aliasName || f.nickname
    const s = searchResults.value.find((x) => x.userId === userId)
    if (s) return s.nickname
    return `#${userId}`
  }

  return {
    friends,
    requests,
    searchResults,
    loading,
    searchLoading,
    error,
    friendTags,
    friendListTagId,
    loadFriendTags,
    loadFriendsAndRequests,
    search,
    addFriend,
    accept,
    reject,
    nicknameForUserId,
  }
})
