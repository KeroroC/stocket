import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { listFailedDeliveries, retryDelivery } from '../api/notification'
import DeliveryFailuresView from './DeliveryFailuresView.vue'

vi.mock('../api/notification', () => ({ listFailedDeliveries: vi.fn(), retryDelivery: vi.fn() }))

beforeEach(() => {
  vi.mocked(listFailedDeliveries).mockResolvedValue({
    content: [{
      id: 'delivery-1', reminderId: 'reminder-1', memberId: 'member-1', channelType: 'WEBHOOK',
      status: 'DEAD', attemptCount: 8, nextAttemptAt: null, lastErrorCode: 'HTTP_400',
      lastErrorAt: '2026-07-14T01:00:00Z', deliveredAt: null, updatedAt: '2026-07-14T01:00:00Z',
    }], page: 0, size: 20, total: 1,
  })
  vi.mocked(retryDelivery).mockResolvedValue({
    id: 'delivery-1', reminderId: 'reminder-1', memberId: 'member-1', channelType: 'WEBHOOK',
    status: 'PENDING', attemptCount: 0, nextAttemptAt: '2026-07-14T02:00:00Z', lastErrorCode: null,
    lastErrorAt: '2026-07-14T01:00:00Z', deliveredAt: null, updatedAt: '2026-07-14T02:00:00Z',
  })
})

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('DeliveryFailuresView', () => {
  it('展示错误分类并反馈手工重试结果', async () => {
    render(DeliveryFailuresView)
    expect(await screen.findByText('HTTP_400')).toBeInTheDocument()
    expect(screen.getByText('WEBHOOK')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '重试 delivery-1' }))
    expect(retryDelivery).toHaveBeenCalledWith('delivery-1')
    await waitFor(() => expect(screen.getByText('已重新排队')).toBeInTheDocument())
  })
})
