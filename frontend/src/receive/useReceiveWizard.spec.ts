import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryDraftRepository } from '../offline/MemoryDraftRepository'
import { createReceiveWizard, type ReceiveWizardServices } from './useReceiveWizard'

const item = { id: 'item-1', name: '牛奶', version: 3, categoryId: 'cat-1', defaultInventoryType: 'BATCH' as const }
const location = { id: 'loc-1', name: '冰箱', version: 2 }

function services(): ReceiveWizardServices {
  return {
    findByBarcode: vi.fn().mockResolvedValue(item),
    resolveLocation: vi.fn().mockResolvedValue(location),
    getAvailability: vi.fn().mockResolvedValue({ totalAvailable: '2' }),
    refreshItem: vi.fn().mockResolvedValue(item),
    refreshLocation: vi.fn().mockResolvedValue(location),
    receive: vi.fn().mockResolvedValue({ id: 'entry-1' }),
  }
}

describe('四步入库向导', () => {
  beforeEach(() => vi.useRealTimers())

  it('按四步前进，扫码命中物品和位置码并在返回后保留字段', async () => {
    const wizard = createReceiveWizard('account-1', new MemoryDraftRepository(), services())

    await wizard.scan({ kind: 'PRODUCT_BARCODE', value: '690001' })
    expect(wizard.state.value.kind).toBe('MATCH')
    expect(wizard.draft.value.item?.name).toBe('牛奶')

    wizard.next()
    await wizard.scan({ kind: 'LOCATION_CODE', value: 'FRIDGE' })
    wizard.updateDetails({ quantity: '3', batchNumber: 'B-01' })
    wizard.next()
    expect(wizard.state.value.kind).toBe('CONFIRM')

    wizard.back()
    expect(wizard.state.value.kind).toBe('DETAILS')
    expect(wizard.draft.value.quantity).toBe('3')
    expect(wizard.draft.value.location?.name).toBe('冰箱')
  })

  it('变更后 300ms 自动保存并可恢复草稿', async () => {
    vi.useFakeTimers()
    const repository = new MemoryDraftRepository()
    const wizard = createReceiveWizard('account-1', repository, services())
    wizard.updateDetails({ quantity: '4' })

    await vi.advanceTimersByTimeAsync(299)
    expect(await repository.list('account-1')).toEqual([])
    await vi.advanceTimersByTimeAsync(1)
    expect(await repository.list('account-1')).toHaveLength(1)

    const restored = createReceiveWizard('account-1', repository, services())
    await restored.restore(wizard.draft.value.id)
    expect(restored.draft.value.quantity).toBe('4')
  })

  it('提交预览保持十进制字符串，同一尝试复用幂等键，编辑后重新生成', async () => {
    const api = services()
    const wizard = createReceiveWizard('account-1', new MemoryDraftRepository(), api)
    wizard.selectItem(item)
    wizard.selectLocation(location)
    wizard.updateDetails({ quantity: '3.50' })
    wizard.goToConfirm()
    const firstKey = wizard.draft.value.idempotencyKey

    await wizard.submit()

    expect(api.receive).toHaveBeenCalledWith(expect.objectContaining({ quantity: '3.50' }), firstKey)
    wizard.updateDetails({ quantity: '4.00' })
    expect(wizard.draft.value.idempotencyKey).not.toBe(firstKey)
  })

  it('版本冲突保留草稿，成功后删除草稿', async () => {
    const repository = new MemoryDraftRepository()
    const api = services()
    vi.mocked(api.refreshItem).mockResolvedValueOnce({ ...item, version: 4 })
    const wizard = createReceiveWizard('account-1', repository, api)
    wizard.selectItem(item)
    wizard.selectLocation(location)
    wizard.goToConfirm()
    await wizard.flush()

    await wizard.submit()
    expect(wizard.state.value.kind).toBe('CONFLICT')
    expect(await repository.get('account-1', wizard.draft.value.id)).toBeDefined()

    vi.mocked(api.refreshItem).mockResolvedValue(item)
    await wizard.submit({ acceptVersions: true })
    expect(wizard.state.value.kind).toBe('COMPLETED')
    expect(await repository.get('account-1', wizard.draft.value.id)).toBeUndefined()
  })
})
