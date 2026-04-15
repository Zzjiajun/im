<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { RouterView } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useVoiceCallStore } from '@/stores/voiceCall'
import { useRouter } from 'vue-router'
import GlobalRealtimeBridge from '@/components/GlobalRealtimeBridge.vue'
import GlobalVoiceCallOverlay from '@/components/GlobalVoiceCallOverlay.vue'

const auth = useAuthStore()
const voiceCall = useVoiceCallStore()
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
  void voiceCall.bootstrap()
})

onUnmounted(() => {
  window.removeEventListener('im-token-refreshed', onTokenRefreshed)
  window.removeEventListener('im-auth-failed', onAuthFailed)
})

watch(
  () => auth.token,
  () => {
    void voiceCall.bootstrap()
  }
)
</script>

<template>
  <div class="app-root">
    <RouterView />
    <GlobalRealtimeBridge />
    <GlobalVoiceCallOverlay />
  </div>
</template>

<style scoped>
.app-root {
  height: 100%;
}
</style>
