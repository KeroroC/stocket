import { beforeEach, describe, expect, it, vi } from 'vitest'
import { searchCatalog } from '../api/catalog'
import { useCatalogSearch } from './useCatalogSearch'

vi.mock('../api/catalog', () => ({ searchCatalog: vi.fn() }))
const search = vi.mocked(searchCatalog)

beforeEach(() => { vi.useFakeTimers(); search.mockReset() })

describe('useCatalogSearch', () => {
  it('防抖搜索并保留精确条码结果', async () => {
    search.mockResolvedValue({ items: [{ id: '1', name: '牛奶', categoryPath: '食品', brand: null, model: null, specification: null, tags: [], barcodes: ['ABC'], matchType: 'BARCODE_EXACT' }], page: 0, size: 20, total: 1 })
    const state = useCatalogSearch()
    state.query.value = ' ABC '
    await vi.advanceTimersByTimeAsync(249)
    expect(search).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(state.results.value[0]?.matchType).toBe('BARCODE_EXACT')
  })

  it('空查询不请求并中止前一请求', async () => {
    search.mockImplementation(() => new Promise(() => {}))
    const state = useCatalogSearch()
    state.query.value = 'first'
    await vi.advanceTimersByTimeAsync(250)
    const firstSignal = search.mock.calls[0]?.[1]?.signal
    state.query.value = 'second'
    await vi.advanceTimersByTimeAsync(250)
    expect(firstSignal?.aborted).toBe(true)
    state.query.value = '   '
    await vi.runAllTimersAsync()
    expect(search).toHaveBeenCalledTimes(2)
  })

  it('映射 problem 为可展示错误', async () => {
    search.mockRejectedValue({ status: 409, code: 'VERSION_CONFLICT', detail: '数据已变化', retryable: false })
    const state = useCatalogSearch()
    state.query.value = 'milk'
    await vi.advanceTimersByTimeAsync(250)
    expect(state.error.value).toBe('数据已变化')
  })
})
