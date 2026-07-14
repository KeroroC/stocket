import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryDraftRepository } from '../offline/MemoryDraftRepository'
import { createReceiveWizard } from '../receive/useReceiveWizard'
import ReceiveWizardView from './ReceiveWizardView.vue'

afterEach(cleanup)

describe('ReceiveWizardView', () => {
  it('展示四步进度和离线提交边界', async () => {
    const wizard = createReceiveWizard('account-1', new MemoryDraftRepository(), {
      findByBarcode: vi.fn(), resolveLocation: vi.fn(),
      getAvailability: vi.fn().mockResolvedValue({ totalAvailable: '0' }),
      refreshItem: vi.fn().mockResolvedValue({ id: 'item-1', name: '牛奶', version: 1, categoryId: 'cat', defaultInventoryType: 'BATCH' }),
      refreshLocation: vi.fn().mockResolvedValue({ id: 'loc-1', name: '冰箱', version: 1 }),
      receive: vi.fn().mockRejectedValue({ code: 'OFFLINE_WRITE_BLOCKED' }),
    })
    wizard.selectItem({ id: 'item-1', name: '牛奶', version: 1, categoryId: 'cat', defaultInventoryType: 'BATCH' })
    wizard.selectLocation({ id: 'loc-1', name: '冰箱', version: 1 })
    wizard.goToConfirm()
    render(ReceiveWizardView, { props: { wizard } })

    expect(screen.getAllByText(/识别|匹配|详情|确认/).length).toBeGreaterThanOrEqual(4)
    await fireEvent.click(screen.getByRole('button', { name: '确认入库' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('联网')
  })
})
