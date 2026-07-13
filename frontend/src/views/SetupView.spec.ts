import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SetupView from './SetupView.vue'
import { initialize } from '../api/identity'

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

const mockInitialize = vi.mocked(initialize)

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('SetupView', () => {
  it('renders form fields for household, timezone, username, display name, password, confirm', () => {
    render(SetupView)

    expect(screen.getByLabelText(/家庭名称/)).toBeInTheDocument()
    expect(screen.getByLabelText(/时区/)).toBeInTheDocument()
    expect(screen.getByLabelText(/管理员用户名/)).toBeInTheDocument()
    expect(screen.getByLabelText(/显示名称/)).toBeInTheDocument()
    expect(screen.getByLabelText('密码')).toBeInTheDocument()
    expect(screen.getByLabelText(/确认密码/)).toBeInTheDocument()
  })

  it('submits validated DTO to parent on success', async () => {
    mockInitialize.mockResolvedValue({
      accountId: 'a1',
      username: 'admin',
      role: 'ADMIN',
    })

    const { emitted } = render(SetupView)

    await fireEvent.update(screen.getByLabelText(/家庭名称/), '我的家庭')
    await fireEvent.update(screen.getByLabelText(/管理员用户名/), 'admin')
    await fireEvent.update(screen.getByLabelText(/显示名称/), '管理员')
    await fireEvent.update(screen.getByLabelText('密码'), 'secureP@ss123')
    await fireEvent.update(screen.getByLabelText(/确认密码/), 'secureP@ss123')
    await fireEvent.click(screen.getByRole('button', { name: /创建家庭/ }))

    await waitFor(() => {
      expect(emitted().success).toBeTruthy()
    })
    const successEvents = emitted().success as unknown[]
    expect(successEvents[0]).toEqual([
      expect.objectContaining({
        householdName: '我的家庭',
        username: 'admin',
        displayName: '管理员',
      }),
    ])
  })

  it('shows validation errors for empty fields', async () => {
    render(SetupView)

    await fireEvent.click(screen.getByRole('button', { name: /创建家庭/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })

  it('shows password mismatch error', async () => {
    render(SetupView)

    await fireEvent.update(screen.getByLabelText(/家庭名称/), '我的家庭')
    await fireEvent.update(screen.getByLabelText(/管理员用户名/), 'admin')
    await fireEvent.update(screen.getByLabelText(/显示名称/), '管理员')
    await fireEvent.update(screen.getByLabelText('密码'), 'secureP@ss123')
    await fireEvent.update(screen.getByLabelText(/确认密码/), 'differentP@ss')
    await fireEvent.click(screen.getByRole('button', { name: /创建家庭/ }))

    await waitFor(() => {
      expect(screen.getByText(/密码不一致/)).toBeInTheDocument()
    })
  })

  it('disables submit button while submitting', async () => {
    let resolveInit: (v: unknown) => void = () => {}
    mockInitialize.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveInit = resolve as (v: unknown) => void
      }),
    )

    render(SetupView)

    await fireEvent.update(screen.getByLabelText(/家庭名称/), '我的家庭')
    await fireEvent.update(screen.getByLabelText(/管理员用户名/), 'admin')
    await fireEvent.update(screen.getByLabelText(/显示名称/), '管理员')
    await fireEvent.update(screen.getByLabelText('密码'), 'secureP@ss123')
    await fireEvent.update(screen.getByLabelText(/确认密码/), 'secureP@ss123')

    const submitBtn = screen.getByRole('button', { name: /创建家庭/ })
    await fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(submitBtn).toBeDisabled()
    })

    resolveInit({ accountId: 'a1', username: 'admin', role: 'ADMIN' })
    await new Promise((r) => setTimeout(r, 0))
  })
})
