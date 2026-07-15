import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  adjustInventory,
  consumeInventory,
  getInventoryAvailability,
  getInventoryEntry,
  getInventoryMovements,
  listInventoryEntries,
  transferInventory,
} from '../api/inventory'
import { listLocations, resolveLocationCode } from '../api/location'
import { listAttachments } from '../api/attachment'
import AdjustSheet from '../components/inventory/AdjustSheet.vue'
import ConsumeSheet from '../components/inventory/ConsumeSheet.vue'
import TransferSheet from '../components/inventory/TransferSheet.vue'
import { FakeScanner } from '../scanner/FakeScanner'
import InventoryEntryView from './InventoryEntryView.vue'

vi.mock('../api/inventory', () => ({
  listInventoryEntries: vi.fn(),
  getInventoryEntry: vi.fn(),
  getInventoryMovements: vi.fn(),
  getInventoryAvailability: vi.fn(),
  consumeInventory: vi.fn(),
  transferInventory: vi.fn(),
  adjustInventory: vi.fn(),
}))
vi.mock('../api/location', () => ({ listLocations: vi.fn(), resolveLocationCode: vi.fn() }))
vi.mock('../api/attachment', () => ({ listAttachments: vi.fn(), uploadAttachment: vi.fn(), deleteAttachment: vi.fn(), downloadAttachment: vi.fn() }))
vi.mock('../api/export', () => ({ downloadCsv: vi.fn() }))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('InventoryEntryView', () => {
  it('shows earliest expiration and movement actor/reason while hiding writes from viewers', async () => {
    vi.mocked(listInventoryEntries).mockResolvedValue({
      items: [{
        id: 'entry-1', itemId: 'item-1', itemName: '牛奶', locationId: 'location-1',
        locationName: '冰箱', type: 'BATCH', quantity: '3.5000', receivedAt: '2026-07-14T00:00:00Z',
        expirationDate: '2026-07-20', archived: false, version: 0,
      }], page: 0, size: 20, total: 1,
    } as never)
    vi.mocked(getInventoryAvailability).mockResolvedValue({
      itemId: 'item-1', totalAvailable: '3.5000', earliestExpiration: '2026-07-20', activeEntryCount: 1,
    })
    vi.mocked(getInventoryMovements).mockResolvedValue([{
      id: 'movement-1', type: 'ADJUSTMENT', quantityDelta: '-0.5', reason: '盘点差异',
      actorAccountId: 'account-1', actorDisplayName: '管理员', requestId: 'request-1',
      occurredAt: '2026-07-14T01:00:00Z',
    }])
    vi.mocked(listAttachments).mockResolvedValue([{ id:'doc-1',ownerType:'INVENTORY_ENTRY',ownerId:'entry-1',purpose:'WARRANTY',filename:'保修单.pdf',mediaType:'application/pdf',sizeBytes:12,status:'AVAILABLE',createdAt:'2026-07-14T00:00:00Z' }])

    render(InventoryEntryView, { props: { role: 'VIEWER' } })

    const summary = await screen.findByLabelText('当前库存摘要')
    expect(within(summary).getByText('2026-07-20')).toBeInTheDocument()
    expect(screen.getByText(/盘点差异/)).toBeInTheDocument()
    expect(screen.getByText(/管理员/)).toBeInTheDocument()
    expect(await screen.findByText('保修单.pdf')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '新增入库' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '库存操作' })).not.toBeInTheDocument()
  })

  it('成员默认选择最早过期批次并提供消耗、调拨和调整操作', async () => {
    vi.mocked(listInventoryEntries).mockResolvedValue({
      items: [
        { id: 'late', itemId: 'item-1', itemName: '牛奶', locationId: 'loc-1', locationName: '冰箱', type: 'BATCH', quantity: '2', receivedAt: '2026-07-14T00:00:00Z', expirationDate: '2026-08-01', archived: false, version: 0 },
        { id: 'early', itemId: 'item-1', itemName: '牛奶', locationId: 'loc-1', locationName: '冰箱', type: 'BATCH', quantity: '1', receivedAt: '2026-07-14T00:00:00Z', expirationDate: '2026-07-20', archived: false, version: 0 },
      ], page: 0, size: 20, total: 2,
    } as never)
    vi.mocked(getInventoryAvailability).mockResolvedValue({ itemId: 'item-1', totalAvailable: '3', earliestExpiration: '2026-07-20', activeEntryCount: 2 })
    vi.mocked(getInventoryMovements).mockResolvedValue([])
    vi.mocked(listAttachments).mockResolvedValue([])
    render(InventoryEntryView, { props: { role: 'MEMBER' } })

    expect(await screen.findByText('推荐：最早到期 2026-07-20')).toBeInTheDocument()
    for (const name of ['消耗', '调拨', '调整']) {
      expect(screen.getByRole('button', { name })).toBeInTheDocument()
    }
    await fireEvent.click(screen.getByRole('button', { name: '消耗' }))
    expect(screen.getByRole('dialog', { name: '消耗库存' })).toBeInTheDocument()
  })

  it('从入库完成页进入时自动选中新创建的库存条目', async () => {
    vi.mocked(listInventoryEntries).mockResolvedValue({
      items: [
        { id: 'early', itemId: 'item-1', itemName: '牛奶', locationId: 'loc-1', locationName: '冰箱', type: 'BATCH', quantity: '1', receivedAt: '2026-07-14T00:00:00Z', expirationDate: '2026-07-20', archived: false, version: 0 },
      ], page: 0, size: 50, total: 51,
    } as never)
    vi.mocked(getInventoryEntry).mockResolvedValue({
      id: 'entry-new', itemId: 'item-2', itemName: '燕麦奶', locationId: 'loc-1', locationName: '冰箱', type: 'BATCH', quantity: '2', receivedAt: '2026-07-15T00:00:00Z', expirationDate: null, archived: false, version: 0,
    })
    vi.mocked(getInventoryAvailability).mockImplementation(async itemId => ({ itemId, totalAvailable: itemId === 'item-2' ? '2' : '1', earliestExpiration: null, activeEntryCount: 1 }))
    vi.mocked(getInventoryMovements).mockResolvedValue([])
    vi.mocked(listAttachments).mockResolvedValue([])

    render(InventoryEntryView, { props: { role: 'MEMBER', entryId: 'entry-new' } })

    expect(await screen.findByRole('heading', { name: '燕麦奶' })).toBeInTheDocument()
    const availabilityMetric = within(screen.getByLabelText('当前库存摘要')).getByText('物品可用量').closest('div')!
    expect(within(availabilityMetric).getByText('2')).toBeInTheDocument()
    expect(getInventoryEntry).toHaveBeenCalledWith('entry-new')
  })

  it('可以按关键词和库存类型缩小条目范围', async () => {
    vi.mocked(listInventoryEntries).mockResolvedValue({
      items: [
        { id: 'batch-1', itemId: 'item-1', itemName: '牛奶', locationId: 'loc-1', locationName: '冰箱', type: 'BATCH', quantity: '2', receivedAt: '2026-07-14T00:00:00Z', expirationDate: '2026-07-20', archived: false, version: 0 },
        { id: 'asset-1', itemId: 'item-2', itemName: '台灯', locationId: 'loc-2', locationName: '书房', type: 'ASSET', quantity: '1', receivedAt: '2026-07-14T00:00:00Z', expirationDate: null, archived: false, version: 0 },
      ], page: 0, size: 20, total: 2,
    } as never)
    vi.mocked(getInventoryAvailability).mockResolvedValue({ itemId: 'item-1', totalAvailable: '2', earliestExpiration: '2026-07-20', activeEntryCount: 1 })
    vi.mocked(getInventoryMovements).mockResolvedValue([])
    vi.mocked(listAttachments).mockResolvedValue([])

    render(InventoryEntryView, { props: { role: 'MEMBER' } })

    await screen.findByRole('button', { name: '牛奶，冰箱，数量 2' })
    await fireEvent.update(screen.getByRole('searchbox', { name: '筛选库存条目' }), '书房')
    expect(screen.queryByRole('button', { name: '牛奶，冰箱，数量 2' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '台灯，书房，数量 1' })).toBeInTheDocument()

    await fireEvent.update(screen.getByRole('searchbox', { name: '筛选库存条目' }), '')
    await fireEvent.click(screen.getByRole('radio', { name: '资产' }))
    expect(screen.queryByRole('button', { name: '牛奶，冰箱，数量 2' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '台灯，书房，数量 1' })).toBeInTheDocument()
  })

  it('消耗数量不足和后端 403 都显示稳定错误', async () => {
    vi.mocked(consumeInventory)
      .mockRejectedValueOnce({ status: 409, detail: '库存数量不足' })
      .mockRejectedValueOnce({ status: 403, detail: '无权限执行库存操作' })
    render(ConsumeSheet, { props: { entryId: 'entry-1', open: true } })

    const consumeField = await screen.findByLabelText('消耗数量')
    await fireEvent.input((consumeField.querySelector?.('input') ?? consumeField) as HTMLInputElement, { target: { value: '9.5' } })
    await fireEvent.click(screen.getByRole('button', { name: '确认消耗' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('库存数量不足')

    await fireEvent.click(screen.getByRole('button', { name: '重试消耗' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('无权限执行库存操作')
  })

  it('扫描位置码后选择目标位置并保持字符串数量提交调拨', async () => {
    const scanner = new FakeScanner()
    vi.mocked(listLocations).mockResolvedValue([
      { id: 'loc-2', parentId: null, name: '冷藏室', fullPath: '厨房 > 冷藏室', publicCode: 'FRIDGE', version: 0, archived: false },
    ])
    vi.mocked(resolveLocationCode).mockResolvedValue({
      id: 'loc-2', parentId: null, name: '冷藏室', fullPath: '厨房 > 冷藏室', publicCode: 'FRIDGE', version: 0, archived: false,
    })
    vi.mocked(transferInventory).mockResolvedValue({ id: 'entry-1' })
    render(TransferSheet, { props: { entryId: 'entry-1', open: true, scanner } })

    await screen.findByRole('dialog', { name: '调拨库存' })
    await fireEvent.click(screen.getByRole('button', { name: '扫描目标位置' }))
    await waitFor(() => scanner.emit('stocket:location:FRIDGE'))
    await waitFor(() => expect(screen.getAllByText('厨房 > 冷藏室').length).toBeGreaterThan(0))
    const transferField = screen.getByLabelText('调拨数量')
    await fireEvent.input((transferField.querySelector?.('input') ?? transferField) as HTMLInputElement, { target: { value: '1.2500' } })
    await fireEvent.click(screen.getByRole('button', { name: '确认调拨' }))

    await waitFor(() => expect(transferInventory).toHaveBeenCalledWith(
      'entry-1', { targetLocationId: 'loc-2', quantity: '1.2500' }, expect.any(String),
    ))
  })

  it('调整库存要求填写原因', async () => {
    render(AdjustSheet, { props: { entryId: 'entry-1', open: true } })
    const adjustField = await screen.findByLabelText('调整后数量')
    await fireEvent.input((adjustField.querySelector?.('input') ?? adjustField) as HTMLInputElement, { target: { value: '2.0000' } })
    await fireEvent.click(screen.getByRole('button', { name: '确认调整' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('请输入调整原因')
    expect(adjustInventory).not.toHaveBeenCalled()
  })
})
