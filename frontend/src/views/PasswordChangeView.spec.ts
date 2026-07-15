import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import PasswordChangeView from './PasswordChangeView.vue'
import { changePassword } from '../api/identity'

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

const mockChangePassword = vi.mocked(changePassword)

beforeEach(() => {
  vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function getOldPassword() {
  const field = screen.getByLabelText('旧密码')
  return (field.querySelector?.('input') ?? field) as HTMLInputElement
}

function getNewPassword() {
  const field = screen.getByLabelText('新密码')
  return (field.querySelector?.('input') ?? field) as HTMLInputElement
}

function getConfirmNewPassword() {
  const field = screen.getByLabelText('确认新密码')
  return (field.querySelector?.('input') ?? field) as HTMLInputElement
}

describe('PasswordChangeView', () => {
  it('renders old password, new password, and confirm password fields', () => {
    render(PasswordChangeView)

    expect(getOldPassword()).toBeInTheDocument()
    expect(getNewPassword()).toBeInTheDocument()
    expect(getConfirmNewPassword()).toBeInTheDocument()
  })

  it('renders only a logout button', () => {
    render(PasswordChangeView)

    expect(screen.getByRole('button', { name: /退出/ })).toBeInTheDocument()
  })

  it('emits success with validated DTO after change', async () => {
    mockChangePassword.mockResolvedValueOnce(undefined)

    const { emitted } = render(PasswordChangeView)

    await fireEvent.input(getOldPassword(), { target: { value: 'oldP@ss1' } })
    await fireEvent.input(getNewPassword(), { target: { value: 'newSecureP@ss12' } })
    await fireEvent.input(getConfirmNewPassword(), { target: { value: 'newSecureP@ss12' } })
    await fireEvent.click(screen.getByRole('button', { name: /修改密码/ }))

    await waitFor(() => {
      expect(emitted().success).toBeTruthy()
    })
  })

  it('clears sensitive fields after success', async () => {
    mockChangePassword.mockResolvedValueOnce(undefined)

    render(PasswordChangeView)

    await fireEvent.input(getOldPassword(), { target: { value: 'oldP@ss1' } })
    await fireEvent.input(getNewPassword(), { target: { value: 'newSecureP@ss12' } })
    await fireEvent.input(getConfirmNewPassword(), { target: { value: 'newSecureP@ss12' } })
    await fireEvent.click(screen.getByRole('button', { name: /修改密码/ }))

    await waitFor(() => {
      expect(getOldPassword()).toHaveValue('')
      expect(getNewPassword()).toHaveValue('')
      expect(getConfirmNewPassword()).toHaveValue('')
    })
  })

  it('shows password mismatch error', async () => {
    render(PasswordChangeView)

    await fireEvent.input(getOldPassword(), { target: { value: 'oldP@ss1' } })
    await fireEvent.input(getNewPassword(), { target: { value: 'newSecureP@ss12' } })
    await fireEvent.input(getConfirmNewPassword(), { target: { value: 'differentP@ss' } })
    await fireEvent.click(screen.getByRole('button', { name: /修改密码/ }))

    await waitFor(() => {
      expect(screen.getByText(/新密码不一致/)).toBeInTheDocument()
    })
  })

  it('shows API error on change failure', async () => {
    mockChangePassword.mockRejectedValueOnce({
      status: 400,
      code: 'INVALID_CREDENTIALS',
    })

    render(PasswordChangeView)

    await fireEvent.input(getOldPassword(), { target: { value: 'wrongOld' } })
    await fireEvent.input(getNewPassword(), { target: { value: 'newSecureP@ss12' } })
    await fireEvent.input(getConfirmNewPassword(), { target: { value: 'newSecureP@ss12' } })
    await fireEvent.click(screen.getByRole('button', { name: /修改密码/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    })
  })
})
