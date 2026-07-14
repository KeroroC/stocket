import { computed, ref } from 'vue'
import { deletePushSubscription, savePushSubscription } from '../api/notification'

function decodeBase64Url(value: string): Uint8Array<ArrayBuffer> {
  const padded = `${value}${'='.repeat((4 - value.length % 4) % 4)}`.replace(/-/g, '+').replace(/_/g, '/')
  const binary = window.atob(padded)
  return Uint8Array.from(binary, character => character.charCodeAt(0))
}

export function usePushSubscription() {
  const enabled = ref(false)
  const busy = ref(false)
  const error = ref('')
  const supported = computed(() => 'Notification' in window && 'serviceWorker' in navigator && 'PushManager' in window)

  async function enable() {
    error.value = ''
    if (!supported.value) throw new Error('当前浏览器不支持通知')
    busy.value = true
    try {
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') throw new Error('通知权限被拒绝')
      const registration = await navigator.serviceWorker.ready
      const publicKey = import.meta.env.VITE_STOCKET_VAPID_PUBLIC_KEY as string | undefined
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        ...(publicKey ? { applicationServerKey: decodeBase64Url(publicKey) } : {}),
      })
      const json = subscription.toJSON()
      if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth) throw new Error('Push subscription 不完整')
      await savePushSubscription({ endpoint: json.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth })
      enabled.value = true
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '启用浏览器通知失败'
      throw cause
    } finally {
      busy.value = false
    }
  }

  async function disable() {
    busy.value = true
    error.value = ''
    try {
      if ('serviceWorker' in navigator) {
        const registration = await navigator.serviceWorker.ready
        const subscription = await registration.pushManager.getSubscription()
        await subscription?.unsubscribe()
      }
      await deletePushSubscription()
      enabled.value = false
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '关闭浏览器通知失败'
      throw cause
    } finally {
      busy.value = false
    }
  }

  return { supported, enabled, busy, error, enable, disable }
}
