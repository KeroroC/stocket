import { onBeforeUnmount, onMounted, ref } from 'vue'

export function useDesktopLayout(query = '(min-width: 1024px)') {
  const isDesktop = ref(false)
  let media: MediaQueryList | null = null
  let update: (() => void) | null = null

  onMounted(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return
    }
    media = window.matchMedia(query)
    update = () => {
      isDesktop.value = media!.matches
    }
    update()
    media.addEventListener('change', update)
  })

  onBeforeUnmount(() => {
    if (media && update) {
      media.removeEventListener('change', update)
    }
  })

  return { isDesktop }
}
