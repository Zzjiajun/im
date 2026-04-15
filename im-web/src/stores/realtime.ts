import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useRealtimeStore = defineStore('realtime', () => {
  const status = ref<'none' | 'live' | 'offline'>('none')

  function setStatus(next: 'none' | 'live' | 'offline') {
    status.value = next
  }

  return {
    status,
    setStatus,
  }
})
