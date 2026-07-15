import { beforeEach, describe, expect, it, vi } from 'vitest'
import { searchCatalog } from '../api/catalog'
import { useCatalogSearch } from './useCatalogSearch'

vi.mock('../api/catalog', () => ({ searchCatalog: vi.fn() }))
const search = vi.mocked(searchCatalog)

beforeEach(() => { vi.useFakeTimers(); search.mockReset() })

describe('useCatalogSearch', () => {
  it('防抖搜索并保留精确条码结果', async () => {
    search.mockResolvedValueOnce({ items: [], page: 0, size: 20, total: 0 })
    search.mockResolvedValue({ items: [{ id: '1', name: '牛奶', categoryPath: '食品', brand: null, model: null, specification: null, tags: [], barcodes: ['ABC'], matchType: 'BARCODE_EXACT' }], page: 0, size: 20, total: 1 })
    const state = useCatalogSearch()
    await vi.runAllTimersAsync()
    search.mockClear()
    state.query.value = ' ABC '
    await vi.advanceTimersByTimeAsync(249)
    expect(search).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    expect(state.results.value[0]?.matchType).toBe('BARCODE_EXACT')
  })

  it('空查询加载全部物品并中止前一请求', async () => {
    search.mockImplementation(() => new Promise(() => {}))
    const state = useCatalogSearch()
    await vi.advanceTimersByTimeAsync(0)
    const initialSignal = search.mock.calls[0]?.[1]?.signal
    state.query.value = 'first'
    await vi.advanceTimersByTimeAsync(250)
    expect(initialSignal?.aborted).toBe(true)
    const firstSignal = search.mock.calls[1]?.[1]?.signal
    state.query.value = 'second'
    await vi.advanceTimersByTimeAsync(250)
    expect(firstSignal?.aborted).toBe(true)
    state.query.value = '   '
    await vi.advanceTimersByTimeAsync(0)
    expect(search).toHaveBeenLastCalledWith('', expect.anything())
    expect(search).toHaveBeenCalledTimes(4)
  })

  it('映射 problem 为可展示错误', async () => {
    search.mockResolvedValueOnce({ items: [], page: 0, size: 20, total: 0 })
    search.mockRejectedValue({ status: 409, code: 'VERSION_CONFLICT', detail: '数据已变化', retryable: false })
    const state = useCatalogSearch()
    await vi.runAllTimersAsync()
    state.query.value = 'milk'
    await vi.advanceTimersByTimeAsync(250)
    expect(state.error.value).toBe('数据已变化')
  })
})
