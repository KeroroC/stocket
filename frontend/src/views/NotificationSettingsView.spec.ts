import { computed, ref } from 'vue'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { listNotificationChannels, updateNotificationChannel } from '../api/notification'
import { usePushSubscription } from '../notification/usePushSubscription'
import NotificationSettingsView from './NotificationSettingsView.vue'

vi.mock('../api/notification', () => ({
  listNotificationChannels: vi.fn(), updateNotificationChannel: vi.fn(),
  savePushSubscription: vi.fn(), deletePushSubscription: vi.fn(),
}))
vi.mock('../notification/usePushSubscription', () => ({ usePushSubscription: vi.fn() }))

const enablePush = vi.fn()
const disablePush = vi.fn()

beforeEach(() => {
  vi.mocked(listNotificationChannels).mockResolvedValue([{
    id: 'smtp-1', type: 'SMTP', enabled: true, hasSecret: true, version: 2,
    configuration: { host: 'smtp.example.com', port: 587, tlsMode: 'STARTTLS', username: 'mailer', fromAddress: 'stocket@example.com' },
  }])
  vi.mocked(updateNotificationChannel).mockResolvedValue({
    id: 'smtp-1', type: 'SMTP', enabled: true, hasSecret: true, version: 3, configuration: {},
  })
  vi.mocked(usePushSubscription).mockReturnValue({
    supported: computed(() => true), enabled: ref(false), busy: ref(false), error: ref(''),
    enable: enablePush, disable: disablePush,
  })
})

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('NotificationSettingsView', () => {
  it('不回显已有密码，空密码保存表示保留旧值', async () => {
    render(NotificationSettingsView)
    const password = await screen.findByLabelText('SMTP 密码') as HTMLInputElement
    expect(password.value).toBe('')
    expect(await screen.findByText('已保存密钥')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '保存 SMTP' }))
    expect(updateNotificationChannel).toHaveBeenCalledWith('SMTP', expect.objectContaining({ secret: '' }))
  })

  it('只在用户点击启用时请求 Push，并展示浏览器拒绝反馈', async () => {
    enablePush.mockRejectedValueOnce(new Error('通知权限被拒绝'))
    render(NotificationSettingsView)
    expect(enablePush).not.toHaveBeenCalled()

    await screen.findByLabelText('SMTP 密码')
    await fireEvent.click(screen.getByRole('button', { name: '启用浏览器通知' }))
    expect(enablePush).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('alert')).toHaveTextContent('通知权限被拒绝')
  })

  it('成功更新 Push subscription', async () => {
    enablePush.mockResolvedValueOnce(undefined)
    render(NotificationSettingsView)
    await screen.findByLabelText('SMTP 密码')
    await fireEvent.click(screen.getByRole('button', { name: '启用浏览器通知' }))
    await waitFor(() => expect(screen.getByText('浏览器通知已启用')).toBeInTheDocument())
  })
})
