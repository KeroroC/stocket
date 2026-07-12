import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AccountView from './AccountView.vue'
import {
  getCurrentAccount,
  updateProfile,
  changePassword,
  getSessions,
  revokeSession,
  revokeOtherSessions,
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
const mockUpdateProfile = vi.mocked(updateProfile)
const mockChangePassword = vi.mocked(changePassword)
const mockGetSessions = vi.mocked(getSessions)
const mockRevokeSession = vi.mocked(revokeSession)
const mockRevokeOtherSessions = vi.mocked(revokeOtherSessions)

const accountFixture = {
  id: 'acc-1',
  username: 'admin',
  displayName: '管理员',
  email: 'admin@example.com',
  role: 'ADMIN',
  mustChangePassword: false,
}

const sessionsFixture = [
  {
    id: 'sess-current',
    createdAt: '2026-07-10T10:00:00Z',
    lastSeenAt: '2026-07-12T08:00:00Z',
    userAgent: 'Chrome/120',
    current: true,
  },
  {
    id: 'sess-other',
    createdAt: '2026-07-09T10:00:00Z',
    lastSeenAt: '2026-07-11T12:00:00Z',
    userAgent: 'Safari/17',
    current: false,
  },
]

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
  mockGetCurrentAccount.mockResolvedValue(accountFixture)
  mockGetSessions.mockResolvedValue(sessionsFixture)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function getNewPasswordInput() {
  return document.getElementById('newPassword') as HTMLInputElement
}

function getConfirmNewPasswordInput() {
  return document.getElementById('confirmNewPassword') as HTMLInputElement
}

describe('AccountView', () => {
  it('loads and displays account profile on mount', async () => {
    render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByLabelText(/显示名称/)).toHaveValue('管理员')
    })
  })

  it('updates profile and emits success', async () => {
    mockUpdateProfile.mockResolvedValueOnce({
      ...accountFixture,
      displayName: '新名称',
    })

    const { emitted } = render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByLabelText(/显示名称/)).toBeInTheDocument()
    })

    await fireEvent.update(screen.getByLabelText(/显示名称/), '新名称')
    await fireEvent.click(screen.getByRole('button', { name: /保存资料/ }))

    await waitFor(() => {
      expect(mockUpdateProfile).toHaveBeenCalledWith({ displayName: '新名称', email: 'admin@example.com' })
      expect(emitted().profileUpdated).toBeTruthy()
    })
  })

  it('shows password change form', async () => {
    render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByLabelText(/当前密码/)).toBeInTheDocument()
      expect(getNewPasswordInput()).toBeInTheDocument()
      expect(getConfirmNewPasswordInput()).toBeInTheDocument()
    })
  })

  it('submits password change', async () => {
    mockChangePassword.mockResolvedValueOnce(undefined)

    render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByLabelText(/当前密码/)).toBeInTheDocument()
    })

    await fireEvent.update(screen.getByLabelText(/当前密码/), 'oldP@ss1')
    await fireEvent.update(getNewPasswordInput(), 'newP@ss123456')
    await fireEvent.update(getConfirmNewPasswordInput(), 'newP@ss123456')
    await fireEvent.click(screen.getByRole('button', { name: /修改密码/ }))

    await waitFor(() => {
      expect(mockChangePassword).toHaveBeenCalledWith({
        oldPassword: 'oldP@ss1',
        newPassword: 'newP@ss123456',
      })
    })
  })

  it('displays session list with current session marked', async () => {
    render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByText(/当前会话/)).toBeInTheDocument()
      expect(screen.getByText(/Chrome/)).toBeInTheDocument()
      expect(screen.getByText(/Safari/)).toBeInTheDocument()
    })
  })

  it('current session cannot be revoked', async () => {
    render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      // Only non-current sessions have a revoke button
      const revokeButtons = screen.getAllByRole('button', { name: /撤销$/ })
      expect(revokeButtons.length).toBe(1) // Only Safari session has revoke
    })
  })

  it('revokes a specific non-current session', async () => {
    mockRevokeSession.mockResolvedValue(undefined)

    render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByText(/Safari/)).toBeInTheDocument()
    })

    const revokeButtons = screen.getAllByRole('button', { name: /撤销$/ })
    await fireEvent.click(revokeButtons[0]!)

    await waitFor(() => {
      expect(mockRevokeSession).toHaveBeenCalledWith('sess-other')
    })
  })

  it('revokes all other sessions', async () => {
    mockRevokeOtherSessions.mockResolvedValue(undefined)

    render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByText(/Safari/)).toBeInTheDocument()
    })

    await fireEvent.click(screen.getByRole('button', { name: /撤销其他全部会话/ }))

    await waitFor(() => {
      expect(mockRevokeOtherSessions).toHaveBeenCalled()
    })
  })

  it('emits logout on 401 error', async () => {
    mockUpdateProfile.mockRejectedValueOnce({ status: 401, code: 'UNAUTHORIZED' })

    const { emitted } = render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByLabelText(/显示名称/)).toBeInTheDocument()
    })

    await fireEvent.update(screen.getByLabelText(/显示名称/), '新名称')
    await fireEvent.click(screen.getByRole('button', { name: /保存资料/ }))

    await waitFor(() => {
      expect(emitted().logout).toBeTruthy()
    })
  })

  it('emits forcePasswordChange on PASSWORD_CHANGE_REQUIRED error', async () => {
    mockUpdateProfile.mockRejectedValueOnce({
      status: 403,
      code: 'PASSWORD_CHANGE_REQUIRED',
    })

    const { emitted } = render(AccountView, { props: { account: accountFixture } })

    await waitFor(() => {
      expect(screen.getByLabelText(/显示名称/)).toBeInTheDocument()
    })

    await fireEvent.update(screen.getByLabelText(/显示名称/), '新名称')
    await fireEvent.click(screen.getByRole('button', { name: /保存资料/ }))

    await waitFor(() => {
      expect(emitted().forcePasswordChange).toBeTruthy()
    })
  })
})
