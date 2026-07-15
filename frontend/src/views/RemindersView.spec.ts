import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { acknowledgeReminder, listReminders } from '../api/reminder'
import type { Reminder } from '../api/reminder'
import RemindersView from './RemindersView.vue'

vi.mock('../api/reminder', () => ({ listReminders: vi.fn(), acknowledgeReminder: vi.fn() }))

const openReminder: Reminder = {
  id: 'reminder-open', itemId: 'item-1', inventoryEntryId: 'entry-1', itemName: '牛奶',
  locationName: '冰箱', availableQuantity: '2', type: 'EXPIRING',
  triggerKey: 'EXPIRING:2026-07-20:1', triggerAt: '2026-07-19T01:00:00Z', status: 'OPEN', version: 0,
}
const acknowledgedReminder: Reminder = {
  ...openReminder, id: 'reminder-ack', type: 'LOW_STOCK', triggerKey: 'LOW_STOCK:3',
  status: 'ACKNOWLEDGED', locationName: null, inventoryEntryId: null,
}

beforeEach(() => {
  vi.mocked(listReminders).mockResolvedValue({
    content: [openReminder, acknowledgedReminder], page: 0, size: 50, total: 2,
  })
  vi.mocked(acknowledgeReminder).mockResolvedValue({ ...openReminder, status: 'ACKNOWLEDGED' })
})

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('RemindersView', () => {
  it('按状态分段展示具体日期、位置和剩余量并确认提醒', async () => {
    render(RemindersView)

    expect(await screen.findAllByText('牛奶')).toHaveLength(2)
    expect(screen.getByText('待处理')).toBeInTheDocument()
    expect(screen.getByText('已确认')).toBeInTheDocument()
    expect(screen.getByText(/冰箱/)).toBeInTheDocument()
    expect(screen.getAllByText(/剩余 2/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/2026/).length).toBeGreaterThan(0)
    expect(document.querySelector('.reminder-kind--expiring')).toHaveTextContent('临期')
    expect(document.querySelector('.reminder-kind--low-stock')).toHaveTextContent('低库存')

    await fireEvent.click(screen.getByRole('button', { name: '确认 牛奶' }))
    expect(acknowledgeReminder).toHaveBeenCalledWith('reminder-open')
    await waitFor(() => expect(screen.queryByRole('button', { name: '确认 牛奶' })).not.toBeInTheDocument())
  })

  it('按类型筛选提醒', async () => {
    render(RemindersView)
    await screen.findByText('待处理')
    await fireEvent.click(screen.getByRole('combobox', { name: '提醒类型' }))
    await fireEvent.click(await screen.findByRole('option', { name: '低库存' }))
    await waitFor(() => expect(listReminders).toHaveBeenLastCalledWith(
      expect.objectContaining({ type: 'LOW_STOCK' }),
    ))
  })
})
