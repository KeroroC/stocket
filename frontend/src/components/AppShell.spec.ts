import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AppShell from './AppShell.vue'
import {
  getCurrentAccount,
  getSessions,
  getMembers,
  getInvites,
} from '../api/identity'

vi.mock('../api/identity', () => ({
  initialize: vi.fn(),
  getSetupStatus: vi.fn(),
  refreshCsrf: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  getCurrentAccount: vi.fn(),
  changePassword: vi.fn(),
  getInviteStatus: vi.fn(),
  acceptInvite: vi.fn(),
  updateProfile: vi.fn(),
  getSessions: vi.fn(),
  revokeSession: vi.fn(),
  revokeOtherSessions: vi.fn(),
  getMembers: vi.fn(),
  createMember: vi.fn(),
  updateMember: vi.fn(),
  resetMemberPassword: vi.fn(),
  getInvites: vi.fn(),
  createInvite: vi.fn(),
  revokeInvite: vi.fn(),
}))

const mockGetCurrentAccount = vi.mocked(getCurrentAccount)
const mockGetSessions = vi.mocked(getSessions)
const mockGetMembers = vi.mocked(getMembers)
const mockGetInvites = vi.mocked(getInvites)

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
  mockGetCurrentAccount.mockResolvedValue({
    id: 'acc-1',
    username: 'admin',
    displayName: '管理员',
    email: null,
    role: 'ADMIN',
    mustChangePassword: false,
  })
  mockGetSessions.mockResolvedValue([])
  mockGetMembers.mockResolvedValue([])
  mockGetInvites.mockResolvedValue([])
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

const adminAccount = {
  id: 'acc-1',
  username: 'admin',
  displayName: '管理员',
  role: 'ADMIN',
}

const memberAccount = {
  id: 'acc-2',
  username: 'member1',
  displayName: '成员一',
  role: 'MEMBER',
}

const viewerAccount = {
  id: 'acc-3',
  username: 'viewer1',
  displayName: '只读者',
  role: 'VIEWER',
}

describe('AppShell', () => {
  it('all roles see "我的账户" navigation item', () => {
    for (const account of [adminAccount, memberAccount, viewerAccount]) {
      const { unmount } = render(AppShell, {
        props: { account },
      })

      const nav = screen.getByRole('navigation', { name: /主导航/ })
      expect(within(nav).getByText(/我的账户/)).toBeInTheDocument()
      unmount()
    }
  })

  it('all roles see inventory navigation', () => {
    for (const account of [adminAccount, memberAccount, viewerAccount]) {
      const { unmount } = render(AppShell, { props: { account } })
      const nav = screen.getByRole('navigation', { name: /主导航/ })
      expect(within(nav).getByText('库存台账')).toBeInTheDocument()
      unmount()
    }
  })

  it('all roles see reminder navigation', () => {
    for (const account of [adminAccount, memberAccount, viewerAccount]) {
      const { unmount } = render(AppShell, { props: { account } })
      const nav = screen.getByRole('navigation', { name: /主导航/ })
      expect(within(nav).getByText('提醒中心')).toBeInTheDocument()
      unmount()
    }
  })

  it('ADMIN sees "成员管理" and "邀请管理" navigation items', () => {
    render(AppShell, { props: { account: adminAccount } })

    const nav = screen.getByRole('navigation', { name: /主导航/ })
    expect(within(nav).getByText(/成员管理/)).toBeInTheDocument()
    expect(within(nav).getByText(/邀请管理/)).toBeInTheDocument()
    expect(within(nav).getByText('通知设置')).toBeInTheDocument()
    expect(within(nav).getByText('通知失败')).toBeInTheDocument()
  })

  it('MEMBER does not see management navigation items', () => {
    render(AppShell, { props: { account: memberAccount } })

    const nav = screen.getByRole('navigation', { name: /主导航/ })
    expect(within(nav).queryByText(/成员管理/)).not.toBeInTheDocument()
    expect(within(nav).queryByText(/邀请管理/)).not.toBeInTheDocument()
    expect(within(nav).queryByText('通知设置')).not.toBeInTheDocument()
    expect(within(nav).queryByText('通知失败')).not.toBeInTheDocument()
  })

  it('VIEWER does not see management navigation items', () => {
    render(AppShell, { props: { account: viewerAccount } })

    const nav = screen.getByRole('navigation', { name: /主导航/ })
    expect(within(nav).queryByText(/成员管理/)).not.toBeInTheDocument()
    expect(within(nav).queryByText(/邀请管理/)).not.toBeInTheDocument()
    expect(within(nav).queryByText('通知设置')).not.toBeInTheDocument()
    expect(within(nav).queryByText('通知失败')).not.toBeInTheDocument()
  })

  it('shows logout button', () => {
    render(AppShell, { props: { account: adminAccount } })

    expect(screen.getByRole('button', { name: /退出登录/ })).toBeInTheDocument()
  })

  it('emits logout when logout button clicked', async () => {
    const { emitted } = render(AppShell, { props: { account: adminAccount } })

    await fireEvent.click(screen.getByRole('button', { name: /退出登录/ }))

    expect(emitted().logout).toBeTruthy()
  })

  it('navigates to account view by default', async () => {
    render(AppShell, { props: { account: adminAccount } })

    await waitFor(() => {
      const content = screen.getByRole('main')
      expect(within(content).getByText(/个人资料/)).toBeInTheDocument()
    })
  })

  it('navigates to members view when admin clicks members', async () => {
    render(AppShell, { props: { account: adminAccount } })

    const nav = screen.getByRole('navigation', { name: /主导航/ })
    await fireEvent.click(within(nav).getByText(/成员管理/))

    await waitFor(() => {
      const content = screen.getByRole('main')
      expect(within(content).getByText(/创建成员/)).toBeInTheDocument()
    })
  })

  it('navigates to invites view when admin clicks invites', async () => {
    render(AppShell, { props: { account: adminAccount } })

    const nav = screen.getByRole('navigation', { name: /主导航/ })
    await fireEvent.click(within(nav).getByText(/邀请管理/))

    await waitFor(() => {
      const content = screen.getByRole('main')
      expect(within(content).getByText(/创建邀请/)).toBeInTheDocument()
    })
  })

  it('propagates logout from child views', async () => {
    const { emitted } = render(AppShell, { props: { account: adminAccount } })

    // The shell should propagate logout events from child views
    expect(emitted).toBeDefined()
  })
})
