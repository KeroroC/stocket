import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginView from './LoginView.vue'
import { login } from '../api/identity'

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

const mockLogin = vi.mocked(login)

function input(label: RegExp) {
  const field = screen.getByLabelText(label)
  return (field.querySelector?.('input') ?? field) as HTMLInputElement
}

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('LoginView', () => {
  it('renders username and password fields', () => {
    render(LoginView)

    expect(screen.getByLabelText(/用户名/)).toBeInTheDocument()
    expect(screen.getByLabelText(/密码/)).toBeInTheDocument()
  })

  it('submits credentials and emits success', async () => {
    mockLogin.mockResolvedValueOnce({
      accountId: 'a1',
      username: 'admin',
      role: 'ADMIN',
    })

    const { emitted } = render(LoginView)

    await fireEvent.input(input(/用户名/), { target: { value: 'admin' } })
    await fireEvent.input(input(/密码/), { target: { value: 'password123' } })
    await fireEvent.click(screen.getByRole('button', { name: /登录/ }))

    await waitFor(() => {
      expect(emitted().success).toBeTruthy()
    })
  })

  it('shows unified error on login failure', async () => {
    mockLogin.mockRejectedValueOnce({
      status: 401,
      code: 'UNAUTHORIZED',
    })

    render(LoginView)

    await fireEvent.input(input(/用户名/), { target: { value: 'admin' } })
    await fireEvent.input(input(/密码/), { target: { value: 'wrong' } })
    await fireEvent.click(screen.getByRole('button', { name: /登录/ }))

    await waitFor(() => {
      expect(screen.getByText('用户名或密码错误')).toBeInTheDocument()
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })

  it('disables submit button while submitting', async () => {
    let resolveLogin: (v: unknown) => void = () => {}
    mockLogin.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveLogin = resolve as (v: unknown) => void
      }),
    )

    render(LoginView)

    await fireEvent.input(input(/用户名/), { target: { value: 'admin' } })
    await fireEvent.input(input(/密码/), { target: { value: 'password123' } })

    const submitBtn = screen.getByRole('button', { name: /登录/ })
    await fireEvent.click(submitBtn)

    await waitFor(() => {
      expect(submitBtn).toBeDisabled()
    })

    resolveLogin({ accountId: 'a1', username: 'admin', role: 'ADMIN' })
    await new Promise((r) => setTimeout(r, 0))
  })
})
