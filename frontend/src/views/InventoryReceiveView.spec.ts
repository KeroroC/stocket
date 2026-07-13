import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { receiveInventory } from '../api/inventory'
import InventoryReceiveView from './InventoryReceiveView.vue'

vi.mock('../api/inventory', () => ({ receiveInventory: vi.fn() }))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

async function fillRequiredFields() {
  await fireEvent.update(screen.getByLabelText('物品 ID'), 'item-1')
  await fireEvent.update(screen.getByLabelText('位置 ID'), 'location-1')
}

describe('InventoryReceiveView', () => {
  it('switches batch and asset fields and keeps quantity as a decimal string', async () => {
    vi.mocked(receiveInventory).mockResolvedValue({ id: 'entry-1' } as never)
    render(InventoryReceiveView, { props: { role: 'MEMBER' } })

    expect(screen.getByLabelText('批次号')).toBeInTheDocument()
    expect(screen.queryByLabelText('资产编号')).not.toBeInTheDocument()

    await fireEvent.update(screen.getByLabelText('库存类型'), 'ASSET')
    expect(screen.queryByLabelText('批次号')).not.toBeInTheDocument()
    expect(screen.getByLabelText('资产编号')).toBeInTheDocument()

    await fillRequiredFields()
    await fireEvent.update(screen.getByLabelText('资产编号'), 'A-001')
    await fireEvent.update(screen.getByLabelText('数量'), '1.0000')
    await fireEvent.click(screen.getByRole('button', { name: '确认入库' }))

    await waitFor(() => expect(receiveInventory).toHaveBeenCalled())
    expect(vi.mocked(receiveInventory).mock.calls[0]?.[0]).toEqual(
      expect.objectContaining({ quantity: '1.0000', type: 'ASSET' }),
    )
  })

  it('keeps form input after a conflict', async () => {
    vi.mocked(receiveInventory).mockRejectedValue({ status: 409, code: 'IDEMPOTENCY_KEY_REUSED' })
    render(InventoryReceiveView, { props: { role: 'MEMBER' } })

    await fillRequiredFields()
    await fireEvent.update(screen.getByLabelText('数量'), '2.5000')
    await fireEvent.click(screen.getByRole('button', { name: '确认入库' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('IDEMPOTENCY_KEY_REUSED')
    expect(screen.getByLabelText('数量')).toHaveValue('2.5000')
  })
})
