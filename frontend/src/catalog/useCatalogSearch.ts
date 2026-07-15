import { getCurrentScope, onScopeDispose, ref, watch } from 'vue'
import { searchCatalog } from '../api/catalog'
import type { ApiProblem } from '../api/http'
import type { CatalogSearchItem } from './catalogModels'

export function useCatalogSearch() {
  const query = ref('')
  const results = ref<CatalogSearchItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined
  let controller: AbortController | undefined

  async function runSearch(normalized: string) {
    const requestController = new AbortController()
    controller = requestController
    loading.value = true
    error.value = null
    try {
      const response = await searchCatalog(normalized, { signal: requestController.signal })
      if (requestController.signal.aborted) return
      results.value = response.items
      total.value = response.total
    } catch (cause) {
      if (!requestController.signal.aborted) {
        const problem = cause as ApiProblem
        error.value = problem.detail ?? problem.code ?? '搜索暂不可用'
      }
    } finally {
      if (!requestController.signal.aborted) loading.value = false
    }
  }

  watch(query, (value) => {
    if (timer) clearTimeout(timer)
    controller?.abort()
    const normalized = value.trim().replace(/\s+/g, ' ')
    if (!normalized) {
      void runSearch('')
      return
    }
    timer = setTimeout(() => void runSearch(normalized), 250)
  }, { flush: 'sync', immediate: true })

  if (getCurrentScope()) onScopeDispose(() => { if (timer) clearTimeout(timer); controller?.abort() })
  return { query, results, total, loading, error }
}
