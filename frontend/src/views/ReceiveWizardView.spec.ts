import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryDraftRepository } from '../offline/MemoryDraftRepository'
import { createReceiveWizard } from '../receive/useReceiveWizard'
import { FakeScanner } from '../scanner/FakeScanner'
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

  it('通过可替换 scanner 依次识别商品和目标位置', async () => {
    const scanner = new FakeScanner()
    const services = {
      findByBarcode: vi.fn().mockResolvedValue({ id: 'item-1', name: '牛奶', version: 1, categoryId: 'cat', defaultInventoryType: 'BATCH' as const }),
      resolveLocation: vi.fn().mockResolvedValue({ id: 'loc-1', name: '冰箱', version: 1 }),
      getAvailability: vi.fn().mockResolvedValue({ totalAvailable: '2' }),
      refreshItem: vi.fn(), refreshLocation: vi.fn(), receive: vi.fn(),
    }
    const wizard = createReceiveWizard('account-1', new MemoryDraftRepository(), services)
    render(ReceiveWizardView, { props: { wizard, scanner } })

    await fireEvent.click(screen.getByRole('button', { name: '扫描商品条码' }))
    await screen.findByRole('dialog', { name: '扫描条码或位置码' })
    scanner.emit('690001')
    expect(await screen.findByText('牛奶')).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    await fireEvent.click(screen.getByRole('button', { name: '扫描位置码' }))
    await screen.findByRole('dialog', { name: '扫描条码或位置码' })
    scanner.emit('stocket:location:FRIDGE')

    await waitFor(() => expect(services.resolveLocation).toHaveBeenCalledWith('FRIDGE'))
    expect(await screen.findByText('位置：冰箱')).toBeInTheDocument()
  })

  it('无需扫码即可从有效位置列表选择存放位置', async () => {
    const services = {
      findByBarcode: vi.fn(), resolveLocation: vi.fn(), getAvailability: vi.fn(),
      refreshItem: vi.fn(), refreshLocation: vi.fn(), receive: vi.fn(),
    }
    const wizard = createReceiveWizard('account-1', new MemoryDraftRepository(), services)
    wizard.selectItem({ id: 'item-1', name: '牛奶', version: 1, categoryId: 'cat', defaultInventoryType: 'BATCH' })
    wizard.next()
    wizard.next()

    render(ReceiveWizardView, {
      props: {
        wizard,
        locations: [
          { id: 'loc-1', name: '冰箱', fullPath: '家 > 厨房 > 冰箱', version: 1 },
          { id: 'loc-2', name: '储物柜', fullPath: '家 > 储物间 > 储物柜', version: 2 },
        ],
      },
    })

    await fireEvent.update(screen.getByLabelText('存放位置'), 'loc-2')
    expect(wizard.draft.value.location).toEqual(expect.objectContaining({ id: 'loc-2', name: '储物柜' }))
    expect(screen.getByText('位置：家 > 储物间 > 储物柜')).toBeInTheDocument()
  })

  it('重新进入页面时恢复当前账号最近草稿', async () => {
    const repository = new MemoryDraftRepository<any>()
    const services = {
      findByBarcode: vi.fn(), resolveLocation: vi.fn(), getAvailability: vi.fn(),
      refreshItem: vi.fn(), refreshLocation: vi.fn(), receive: vi.fn(),
    }
    const original = createReceiveWizard('account-1', repository, services)
    original.selectItem({ id: 'item-1', name: '牛奶', version: 1, categoryId: 'cat', defaultInventoryType: 'BATCH' })
    original.selectLocation({ id: 'loc-1', name: '冰箱', version: 1 })
    original.updateDetails({ quantity: '4.2500' })
    await original.flush()

    const restored = createReceiveWizard('account-1', repository, services)
    render(ReceiveWizardView, { props: { wizard: restored, draftRepository: repository, account: { id: 'account-1', username: 'member', displayName: '成员', role: 'MEMBER' } } })

    expect(await screen.findByDisplayValue('4.2500')).toBeInTheDocument()
    expect(screen.getByText('位置：冰箱')).toBeInTheDocument()
  })
})
