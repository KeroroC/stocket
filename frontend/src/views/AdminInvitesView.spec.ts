import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminInvitesView from './AdminInvitesView.vue'
import * as identityApi from '../api/identity'

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
  getInvites: vi.fn().mockResolvedValue([]),
  createInvite: vi.fn(),
  revokeInvite: vi.fn(),
  extendInvite: vi.fn(),
}))

const invitesFixture = [
  {
    id: 'inv-1',
    role: 'MEMBER',
    expiresAt: '2026-07-13T12:00:00Z',
    status: 'PENDING',
    createdAt: '2026-07-12T12:00:00Z',
    useCount: 0,
    maxUses: 1,
    acceptedBy: [],
  },
  {
    id: 'inv-2',
    role: 'VIEWER',
    expiresAt: '2026-07-11T12:00:00Z',
    status: 'EXPIRED',
    createdAt: '2026-07-10T12:00:00Z',
    useCount: 0,
    maxUses: 1,
    acceptedBy: [],
  },
  {
    id: 'inv-3',
    role: 'MEMBER',
    expiresAt: '2026-07-14T12:00:00Z',
    status: 'ACCEPTED',
    createdAt: '2026-07-09T12:00:00Z',
    useCount: 1,
    maxUses: 1,
    acceptedBy: ['接受用户'],
  },
]

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
  vi.mocked(identityApi.getInvites).mockResolvedValue(invitesFixture)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function getOpenDialog(): HTMLElement {
  const dialogs = document.querySelectorAll('.el-overlay-dialog')
  for (const d of dialogs) {
    if ((d as HTMLElement).style.display !== 'none') return d as HTMLElement
  }
  return dialogs[dialogs.length - 1]! as HTMLElement
}

describe('AdminInvitesView', () => {
  it('renders invite list on mount', async () => {
    render(AdminInvitesView)

    await waitFor(() => {
      // Check that invite statuses are shown
      expect(screen.getByText(/待使用/)).toBeInTheDocument()
      expect(screen.getByText(/已过期/)).toBeInTheDocument()
      expect(screen.getByText(/已接受/)).toBeInTheDocument()
    })
  })

  it('shows invite statuses', async () => {
    render(AdminInvitesView)

    await waitFor(() => {
      expect(screen.getByText(/待使用/)).toBeInTheDocument()
      expect(screen.getByText(/已过期/)).toBeInTheDocument()
      expect(screen.getByText(/已接受/)).toBeInTheDocument()
    })
  })

  it('opens create invite dialog', async () => {
    render(AdminInvitesView)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /创建邀请/ })).toBeInTheDocument()
    })

    await fireEvent.click(screen.getByRole('button', { name: /创建邀请/ }))

    await waitFor(() => {
      const dialog = getOpenDialog()
      expect(within(dialog as HTMLElement).getByText('角色')).toBeInTheDocument()
      expect(within(dialog as HTMLElement).getByText(/有效期/)).toBeInTheDocument()
    })
  })

  it('creates invite and shows one-time link', async () => {
    vi.mocked(identityApi.createInvite).mockResolvedValueOnce({
      inviteId: 'inv-new',
      inviteLink: 'https://example.com/invite/abc123token',
    })

    render(AdminInvitesView)

    await fireEvent.click(screen.getByRole('button', { name: /创建邀请/ }))

    await waitFor(() => {
      const dialog = getOpenDialog()
      expect(within(dialog as HTMLElement).getByText('角色')).toBeInTheDocument()
    })

    const dialog = getOpenDialog() as HTMLElement
    await fireEvent.click(within(dialog).getByText('确认创建'))

    await waitFor(() => {
      expect(identityApi.createInvite).toHaveBeenCalledWith({
        role: 'MEMBER',
        expiresAt: expect.any(String),
        maxUses: 1,
      })
      expect(screen.getByText(/abc123token/)).toBeInTheDocument()
    })
  })

  it('clears invite link from state after closing result dialog', async () => {
    vi.mocked(identityApi.createInvite).mockResolvedValueOnce({
      inviteId: 'inv-new',
      inviteLink: 'https://example.com/invite/abc123token',
    })

    render(AdminInvitesView)

    await fireEvent.click(screen.getByRole('button', { name: /创建邀请/ }))

    await waitFor(() => {
      const dialog = getOpenDialog()
      expect(within(dialog as HTMLElement).getByText('角色')).toBeInTheDocument()
    })

    const dialog = getOpenDialog() as HTMLElement
    await fireEvent.click(within(dialog).getByText('确认创建'))

    await waitFor(() => {
      expect(screen.getByText(/abc123token/)).toBeInTheDocument()
    })

    // Close the result dialog
    const okButtons = screen.getAllByText('确定')
    await fireEvent.click(okButtons[okButtons.length - 1]!)

    await waitFor(() => {
      expect(screen.queryByText(/abc123token/)).not.toBeInTheDocument()
    })
  })

  it('invite list only shows metadata, not sensitive tokens', async () => {
    render(AdminInvitesView)

    await waitFor(() => {
      expect(screen.getByText(/待使用/)).toBeInTheDocument()
    })

    // Should not display any token strings
    expect(screen.queryByText(/token/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/abc123/)).not.toBeInTheDocument()
  })

  it('revokes a pending invite', async () => {
    vi.mocked(identityApi.revokeInvite).mockResolvedValueOnce(undefined)

    render(AdminInvitesView)

    await waitFor(() => {
      expect(screen.getByText(/待使用/)).toBeInTheDocument()
    })

    const revokeButtons = screen.getAllByText('撤销')
    await fireEvent.click(revokeButtons[0]!)

    await waitFor(() => {
      expect(vi.mocked(identityApi.revokeInvite)).toHaveBeenCalledWith('inv-1')
    })
  })

  it('emits logout on 401 error', async () => {
    vi.mocked(identityApi.getInvites).mockRejectedValueOnce({ status: 401, code: 'UNAUTHORIZED' })

    const { emitted } = render(AdminInvitesView)

    await waitFor(() => {
      expect(emitted().logout).toBeTruthy()
    })
  })

  it('emits forcePasswordChange on PASSWORD_CHANGE_REQUIRED error', async () => {
    vi.mocked(identityApi.getInvites).mockRejectedValueOnce({
      status: 403,
      code: 'PASSWORD_CHANGE_REQUIRED',
    })

    const { emitted } = render(AdminInvitesView)

    await waitFor(() => {
      expect(emitted().forcePasswordChange).toBeTruthy()
    })
  })

  it('shows acceptedBy for accepted invites', async () => {
    render(AdminInvitesView)

    await waitFor(() => {
      expect(screen.getByText(/接受者：接受用户/)).toBeInTheDocument()
    })
  })

  it('shows use count for multi-use invites', async () => {
    const multiUseFixture = [
      {
        id: 'inv-multi',
        role: 'MEMBER',
        expiresAt: '2026-07-14T12:00:00Z',
        status: 'PENDING',
        createdAt: '2026-07-12T12:00:00Z',
        useCount: 2,
        maxUses: 5,
        acceptedBy: ['用户1', '用户2'],
      },
    ]
    vi.mocked(identityApi.getInvites).mockResolvedValue(multiUseFixture)

    render(AdminInvitesView)

    await waitFor(() => {
      expect(screen.getByText(/使用次数：2\/5/)).toBeInTheDocument()
    })
  })

  it('shows extend button only for pending invites', async () => {
    render(AdminInvitesView)

    await waitFor(() => {
      const extendButtons = screen.getAllByText('延长')
      expect(extendButtons).toHaveLength(1) // Only inv-1 is PENDING
    })
  })

  it('uses the shared visible action style for the extend button', async () => {
    render(AdminInvitesView)

    const extendButton = await screen.findByRole('button', { name: '延长' })

    expect(extendButton).toHaveClass('el-button')
    expect(extendButton).not.toHaveClass('extend-btn')
  })
})
