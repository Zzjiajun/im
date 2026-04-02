<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

function onTokenRefreshed() {
  auth.syncTokenFromStorage()
}

function onAuthFailed() {
  auth.syncTokenFromStorage()
  if (!auth.isLoggedIn && router.currentRoute.value.meta.requiresAuth) {
    router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

onMounted(() => {
  window.addEventListener('im-token-refreshed', onTokenRefreshed)
  window.addEventListener('im-auth-failed', onAuthFailed)
})

onUnmounted(() => {
  window.removeEventListener('im-token-refreshed', onTokenRefreshed)
  window.removeEventListener('im-auth-failed', onAuthFailed)
})
</script>

<template>
  <RouterView />
</template>
