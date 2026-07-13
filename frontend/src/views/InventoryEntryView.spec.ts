import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getInventoryAvailability, getInventoryMovements, listInventoryEntries } from '../api/inventory'
import InventoryEntryView from './InventoryEntryView.vue'

vi.mock('../api/inventory', () => ({
  listInventoryEntries: vi.fn(),
  getInventoryMovements: vi.fn(),
  getInventoryAvailability: vi.fn(),
}))

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

    render(InventoryEntryView, { props: { role: 'VIEWER' } })

    expect(await screen.findByText('最早到期：2026-07-20')).toBeInTheDocument()
    expect(screen.getByText(/盘点差异/)).toBeInTheDocument()
    expect(screen.getByText(/管理员/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '新增入库' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '库存操作' })).not.toBeInTheDocument()
  })
})
