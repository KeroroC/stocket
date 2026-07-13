import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { consumeInventory } from '../api/inventory'
import InventoryOperateView from './InventoryOperateView.vue'

vi.mock('../api/inventory', () => ({ consumeInventory: vi.fn() }))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('InventoryOperateView', () => {
  it('reuses one idempotency key for retry and creates a new key after input changes', async () => {
    vi.mocked(consumeInventory)
      .mockRejectedValueOnce({ code: 'NETWORK_ERROR' })
      .mockResolvedValue({ id: 'entry-1' } as never)
    render(InventoryOperateView, { props: { role: 'MEMBER', entryId: 'entry-1' } })

    await fireEvent.update(screen.getByLabelText('操作数量'), '1.2500')
    await fireEvent.click(screen.getByRole('button', { name: '确认操作' }))
    await screen.findByRole('alert')
    await fireEvent.click(screen.getByRole('button', { name: '重试' }))

    await waitFor(() => expect(consumeInventory).toHaveBeenCalledTimes(2))
    const firstKey = vi.mocked(consumeInventory).mock.calls[0]?.[2]
    const retryKey = vi.mocked(consumeInventory).mock.calls[1]?.[2]
    expect(firstKey).toBeTruthy()
    expect(retryKey).toBe(firstKey)

    vi.mocked(consumeInventory).mockResolvedValue({ id: 'entry-1' } as never)
    await fireEvent.update(screen.getByLabelText('操作数量'), '2.0000')
    await fireEvent.click(screen.getByRole('button', { name: '确认操作' }))
    await waitFor(() => expect(consumeInventory).toHaveBeenCalledTimes(3))
    expect(vi.mocked(consumeInventory).mock.calls[2]?.[2]).not.toBe(firstKey)
    expect(vi.mocked(consumeInventory).mock.calls[2]?.[1]).toEqual({ quantity: '2.0000' })
  })
})
