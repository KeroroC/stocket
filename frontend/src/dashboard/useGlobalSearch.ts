import { ref, watch } from 'vue'
import { getDashboard } from '../api/dashboard'
import type { DashboardSearchItem } from './DashboardSummary'

export function useGlobalSearch() {
  const query = ref('')
  const results = ref<DashboardSearchItem[]>([])
  const loading = ref(false)
  const error = ref('')
  let timer: ReturnType<typeof setTimeout> | undefined
  let controller: AbortController | undefined

  async function execute(value: string) {
    controller?.abort()
    controller = new AbortController()
    loading.value = true
    error.value = ''
    try {
      results.value = (await getDashboard(value, controller.signal)).search
    } catch (cause) {
      if ((cause as { name?: string }).name !== 'AbortError') error.value = '搜索失败，请稍后重试。'
    } finally {
      loading.value = false
    }
  }

  watch(query, (value) => {
    if (timer) clearTimeout(timer)
    const normalized = value.trim()
    if (!normalized) {
      results.value = []
      return
    }
    const exactBarcode = /^[A-Za-z0-9_-]{4,}$/.test(normalized)
    if (exactBarcode) void execute(normalized)
    else timer = setTimeout(() => void execute(normalized), 250)
  })

  return { query, results, loading, error, execute }
}
