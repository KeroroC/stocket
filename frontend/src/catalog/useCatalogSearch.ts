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

  watch(query, (value) => {
    if (timer) clearTimeout(timer)
    controller?.abort()
    const normalized = value.trim().replace(/\s+/g, ' ')
    if (!normalized) { results.value = []; total.value = 0; loading.value = false; error.value = null; return }
    timer = setTimeout(async () => {
      controller = new AbortController()
      loading.value = true
      error.value = null
      try {
        const response = await searchCatalog(normalized, { signal: controller.signal })
        results.value = response.items
        total.value = response.total
      } catch (cause) {
        if (!controller.signal.aborted) {
          const problem = cause as ApiProblem
          error.value = problem.detail ?? problem.code ?? '搜索暂不可用'
        }
      } finally {
        if (!controller.signal.aborted) loading.value = false
      }
    }, 250)
  }, { flush: 'sync' })

  if (getCurrentScope()) onScopeDispose(() => { if (timer) clearTimeout(timer); controller?.abort() })
  return { query, results, total, loading, error }
}
