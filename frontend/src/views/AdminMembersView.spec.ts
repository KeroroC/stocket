import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminMembersView from './AdminMembersView.vue'
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
  getMembers: vi.fn().mockResolvedValue([]),
  createMember: vi.fn(),
  updateMember: vi.fn(),
  resetMemberPassword: vi.fn(),
  enableMember: vi.fn(),
  disableMember: vi.fn(),
  getInvites: vi.fn(),
  createInvite: vi.fn(),
  revokeInvite: vi.fn(),
}))

const membersFixture = [
  {
    id: 'mem-1',
    accountId: 'acc-1',
    username: 'admin',
    displayName: '管理员',
    role: 'ADMIN',
    enabled: true,
    createdAt: '2026-07-01T00:00:00Z',
  },
  {
    id: 'mem-2',
    accountId: 'acc-2',
    username: 'member1',
    displayName: '成员一',
    role: 'MEMBER',
    enabled: true,
    createdAt: '2026-07-05T00:00:00Z',
  },
  {
    id: 'mem-3',
    accountId: 'acc-3',
    username: 'viewer1',
    displayName: '只读者',
    role: 'VIEWER',
    enabled: false,
    createdAt: '2026-07-06T00:00:00Z',
  },
]

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
  vi.mocked(identityApi.getMembers).mockResolvedValue(membersFixture)
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

describe('AdminMembersView', () => {
  it('renders member list on mount', async () => {
    render(AdminMembersView)

    await waitFor(() => {
      expect(screen.getByText('@admin')).toBeInTheDocument()
      expect(screen.getByText('@member1')).toBeInTheDocument()
      expect(screen.getByText('@viewer1')).toBeInTheDocument()
    })
  })

  it('opens create member dialog', async () => {
    render(AdminMembersView)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /创建成员/ })).toBeInTheDocument()
    })

    await fireEvent.click(screen.getByRole('button', { name: /创建成员/ }))

    await waitFor(() => {
      const dialog = getOpenDialog()
      expect(dialog).toBeTruthy()
      expect(within(dialog as HTMLElement).getByText('用户名')).toBeInTheDocument()
      expect(within(dialog as HTMLElement).getByText('显示名称')).toBeInTheDocument()
    })
  })

  it('creates member and shows one-time temporary password', async () => {
    vi.mocked(identityApi.createMember).mockResolvedValueOnce({
      memberId: 'mem-new',
      accountId: 'acc-new',
      temporaryPassword: 'TempP@ss123',
    })

    render(AdminMembersView)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /创建成员/ })).toBeInTheDocument()
    })

    await fireEvent.click(screen.getByRole('button', { name: /创建成员/ }))

    await waitFor(() => {
      const dialog = getOpenDialog()
      expect(within(dialog as HTMLElement).getByText('用户名')).toBeInTheDocument()
    })

    const dialog = getOpenDialog() as HTMLElement
    const usernameInput = within(dialog).getByText('用户名').closest('.form-field')!.querySelector('input')!
    const displayNameInput = within(dialog).getByText('显示名称').closest('.form-field')!.querySelector('input')!
    const roleSelect = within(dialog).getByText('角色').closest('.form-field')!.querySelector('select')!

    await fireEvent.update(usernameInput, 'newuser')
    await fireEvent.update(displayNameInput, '新用户')
    await fireEvent.update(roleSelect, 'MEMBER')

    await fireEvent.click(within(dialog).getByText('确认创建'))

    await waitFor(() => {
      expect(screen.getByText('TempP@ss123')).toBeInTheDocument()
    })
  })

  it('clears temporary password from state after closing result dialog', async () => {
    vi.mocked(identityApi.createMember).mockResolvedValueOnce({
      memberId: 'mem-new',
      accountId: 'acc-new',
      temporaryPassword: 'TempP@ss123',
    })

    render(AdminMembersView)

    await fireEvent.click(screen.getByRole('button', { name: /创建成员/ }))

    await waitFor(() => {
      const dialog = getOpenDialog()
      expect(within(dialog as HTMLElement).getByText('用户名')).toBeInTheDocument()
    })

    const dialog = getOpenDialog() as HTMLElement
    const usernameInput = within(dialog).getByText('用户名').closest('.form-field')!.querySelector('input')!
    const displayNameInput = within(dialog).getByText('显示名称').closest('.form-field')!.querySelector('input')!
    const roleSelect = within(dialog).getByText('角色').closest('.form-field')!.querySelector('select')!

    await fireEvent.update(usernameInput, 'newuser')
    await fireEvent.update(displayNameInput, '新用户')
    await fireEvent.update(roleSelect, 'MEMBER')
    await fireEvent.click(within(dialog).getByText('确认创建'))

    await waitFor(() => {
      expect(screen.getByText('TempP@ss123')).toBeInTheDocument()
    })

    // Close the result dialog
    const okButtons = screen.getAllByText('确定')
    await fireEvent.click(okButtons[okButtons.length - 1]!)

    await waitFor(() => {
      expect(screen.queryByText('TempP@ss123')).not.toBeInTheDocument()
    })
  })

  it('changes member role', async () => {
    vi.mocked(identityApi.updateMember).mockResolvedValueOnce({
      ...membersFixture[1],
      role: 'VIEWER',
    } as identityApi.MemberInfo)

    render(AdminMembersView)

    await waitFor(() => {
      expect(screen.getByText('@member1')).toBeInTheDocument()
    })

    // Find and click the edit role action for member1
    const editButtons = screen.getAllByText('修改角色')
    await fireEvent.click(editButtons[1]!) // Second member button (index 1)

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: /修改角色/ })).toBeInTheDocument()
    })

    const dialog = screen.getByRole('dialog', { name: /修改角色/ })
    const roleSelect = within(dialog).getByText('新角色').closest('.form-field')!.querySelector('select')!
    await fireEvent.update(roleSelect, 'VIEWER')
    await fireEvent.click(within(dialog).getByText('确认'))

    await waitFor(() => {
      expect(vi.mocked(identityApi.updateMember)).toHaveBeenCalledWith('mem-2', { role: 'VIEWER' })
    })
  })

  it('toggles member enabled/disabled status', async () => {
    vi.mocked(identityApi.enableMember).mockResolvedValueOnce({
      ...membersFixture[2],
      enabled: true,
    } as identityApi.MemberInfo)

    render(AdminMembersView)

    await waitFor(() => {
      expect(screen.getByText('@viewer1')).toBeInTheDocument()
    })

    const enableButtons = screen.getAllByText('启用')
    await fireEvent.click(enableButtons[0]!)

    await waitFor(() => {
      expect(vi.mocked(identityApi.enableMember)).toHaveBeenCalledWith('mem-3')
    })
  })

  it('resets member password and shows one-time temporary password', async () => {
    vi.mocked(identityApi.resetMemberPassword).mockResolvedValueOnce({
      temporaryPassword: 'NewTempP@ss456',
    })

    render(AdminMembersView)

    await waitFor(() => {
      expect(screen.getByText('@member1')).toBeInTheDocument()
    })

    const resetButtons = screen.getAllByText('重置密码')
    await fireEvent.click(resetButtons[1]!) // Second member

    await waitFor(() => {
      expect(screen.getByText('NewTempP@ss456')).toBeInTheDocument()
      expect(vi.mocked(identityApi.resetMemberPassword)).toHaveBeenCalledWith('mem-2')
    })
  })

  it('shows Chinese error for LAST_ADMIN_REQUIRED', async () => {
    vi.mocked(identityApi.updateMember).mockRejectedValueOnce({
      status: 409,
      code: 'LAST_ADMIN_REQUIRED',
    })

    render(AdminMembersView)

    await waitFor(() => {
      expect(screen.getByText('@admin')).toBeInTheDocument()
    })

    // Try to change admin role
    const editButtons = screen.getAllByText('修改角色')
    await fireEvent.click(editButtons[0]!)

    await waitFor(() => {
      expect(screen.getByRole('dialog', { name: /修改角色/ })).toBeInTheDocument()
    })

    const dialog = screen.getByRole('dialog', { name: /修改角色/ })
    const roleSelect = within(dialog).getByText('新角色').closest('.form-field')!.querySelector('select')!
    await fireEvent.update(roleSelect, 'MEMBER')
    await fireEvent.click(within(dialog).getByText('确认'))

    await waitFor(() => {
      expect(within(dialog).getByText(/不能移除或降级最后一位管理员/)).toBeInTheDocument()
    })
  })

  it('emits logout on 401 error', async () => {
    vi.mocked(identityApi.getMembers).mockRejectedValueOnce({ status: 401, code: 'UNAUTHORIZED' })

    const { emitted } = render(AdminMembersView)

    await waitFor(() => {
      expect(emitted().logout).toBeTruthy()
    })
  })
})
