import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import InviteAcceptView from './InviteAcceptView.vue'
import { getInviteStatus, acceptInvite } from '../api/identity'

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
}))

const mockGetInviteStatus = vi.mocked(getInviteStatus)
const mockAcceptInvite = vi.mocked(acceptInvite)

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('InviteAcceptView', () => {
  it('fetches invite status using token and shows role', async () => {
    mockGetInviteStatus.mockResolvedValueOnce({
      available: true,
      role: 'MEMBER',
      expiresAt: '2026-07-20T00:00:00Z',
    })

    render(InviteAcceptView, { props: { token: 'tok123' } })

    await waitFor(() => {
      expect(mockGetInviteStatus).toHaveBeenCalledWith('tok123')
      expect(screen.getByText('MEMBER')).toBeInTheDocument()
    })
  })

  it('shows expiry time', async () => {
    mockGetInviteStatus.mockResolvedValueOnce({
      available: true,
      role: 'ADMIN',
      expiresAt: '2026-07-20T00:00:00Z',
    })

    render(InviteAcceptView, { props: { token: 'tok123' } })

    await waitFor(() => {
      expect(screen.getByText(/2026/)).toBeInTheDocument()
    })
  })

  it('submits validated DTO with username, displayName, password', async () => {
    mockGetInviteStatus.mockResolvedValueOnce({
      available: true,
      role: 'MEMBER',
      expiresAt: '2026-07-20T00:00:00Z',
    })
    mockAcceptInvite.mockResolvedValueOnce({
      accountId: 'a1',
      memberId: 'm1',
    })

    const { emitted } = render(InviteAcceptView, { props: { token: 'tok123' } })

    await waitFor(() => {
      expect(screen.getByLabelText(/用户名/)).toBeInTheDocument()
    })

    await fireEvent.update(screen.getByLabelText(/用户名/), 'newuser')
    await fireEvent.update(screen.getByLabelText(/显示名称/), '新用户')
    await fireEvent.update(screen.getByLabelText('密码'), 'secureP@ss1')
    await fireEvent.update(screen.getByLabelText(/确认密码/), 'secureP@ss1')
    await fireEvent.click(screen.getByRole('button', { name: /接受邀请/ }))

    await waitFor(() => {
      expect(emitted().success).toBeTruthy()
    })
    const successEvents = emitted().success as unknown[]
    expect(successEvents[0]).toEqual([
      expect.objectContaining({
        username: 'newuser',
        displayName: '新用户',
      }),
    ])
  })

  it('shows error when invite status fetch fails', async () => {
    mockGetInviteStatus.mockRejectedValueOnce({
      status: 404,
      code: 'INVITE_NOT_FOUND',
    })

    render(InviteAcceptView, { props: { token: 'badtoken' } })

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })

  it('shows password mismatch error', async () => {
    mockGetInviteStatus.mockResolvedValueOnce({
      available: true,
      role: 'MEMBER',
      expiresAt: '2026-07-20T00:00:00Z',
    })

    render(InviteAcceptView, { props: { token: 'tok123' } })

    await waitFor(() => {
      expect(screen.getByLabelText(/用户名/)).toBeInTheDocument()
    })

    await fireEvent.update(screen.getByLabelText(/用户名/), 'newuser')
    await fireEvent.update(screen.getByLabelText(/显示名称/), '新用户')
    await fireEvent.update(screen.getByLabelText('密码'), 'secureP@ss1')
    await fireEvent.update(screen.getByLabelText(/确认密码/), 'differentP@ss')
    await fireEvent.click(screen.getByRole('button', { name: /接受邀请/ }))

    await waitFor(() => {
      expect(screen.getByText(/密码不一致/)).toBeInTheDocument()
    })
  })

  it('does not expose invite token in rendered output', async () => {
    mockGetInviteStatus.mockResolvedValueOnce({
      available: true,
      role: 'MEMBER',
      expiresAt: '2026-07-20T00:00:00Z',
    })

    render(InviteAcceptView, { props: { token: 'secrettoken123' } })

    await waitFor(() => {
      expect(screen.getByLabelText(/用户名/)).toBeInTheDocument()
    })

    expect(screen.queryByText('secrettoken123')).not.toBeInTheDocument()
  })
})
