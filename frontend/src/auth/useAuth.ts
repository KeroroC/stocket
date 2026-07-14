import { ref } from 'vue'
import { getCurrentAccount, getSetupStatus, initialize as apiInitialize, login as apiLogin, logout as apiLogout, refreshCsrf, changePassword as apiChangePassword } from '../api/identity'
import type { AuthState, CurrentAccount } from './AuthState'
import type { ChangePasswordRequest, InitializeRequest, LoginRequest } from '../api/identity'
import {
  clearActiveSessionData,
  registerSessionExpiredHandler,
  setActiveSessionAccount,
} from '../offline/sessionCleanup'

export const authState = ref<AuthState>({ kind: 'checking-setup' })

export function useAuth() {
  const state = authState
  state.value = { kind: 'checking-setup' }
  setActiveSessionAccount(undefined)
  registerSessionExpiredHandler(() => {
    state.value = { kind: 'anonymous' }
  })

  function mapAccount(raw: { id: string; username: string; displayName: string; role: string; mustChangePassword: boolean }): CurrentAccount {
    return {
      id: raw.id,
      username: raw.username,
      displayName: raw.displayName,
      role: raw.role,
    }
  }

  async function bootstrap(): Promise<void> {
    state.value = { kind: 'checking-setup' }

    const setupStatus = await getSetupStatus()

    await refreshCsrf()

    if (!setupStatus.initialized) {
      state.value = { kind: 'setup-required' }
      return
    }

    try {
      const account = await getCurrentAccount()
      if (account.mustChangePassword) {
        state.value = { kind: 'password-change-required', account: mapAccount(account) }
      } else {
        state.value = { kind: 'authenticated', account: mapAccount(account) }
        setActiveSessionAccount(account.id)
      }
    } catch {
      state.value = { kind: 'anonymous' }
    }
  }

  async function initialize(data: InitializeRequest): Promise<void> {
    await refreshCsrf()
    await apiInitialize(data)
    await refreshCsrf()
    const account = await getCurrentAccount()
    if (account.mustChangePassword) {
      state.value = { kind: 'password-change-required', account: mapAccount(account) }
    } else {
      state.value = { kind: 'authenticated', account: mapAccount(account) }
      setActiveSessionAccount(account.id)
    }
  }

  async function login(data: LoginRequest): Promise<void> {
    await apiLogin(data)
    await refreshCsrf()
    const account = await getCurrentAccount()
    if (account.mustChangePassword) {
      state.value = { kind: 'password-change-required', account: mapAccount(account) }
    } else {
      state.value = { kind: 'authenticated', account: mapAccount(account) }
      setActiveSessionAccount(account.id)
    }
  }

  async function logout(): Promise<void> {
    try {
      await apiLogout()
    } catch {
      // ignore logout failure
    }
    await clearActiveSessionData()
    state.value = { kind: 'anonymous' }
  }

  async function passwordChanged(): Promise<void> {
    const account = await getCurrentAccount()
    state.value = { kind: 'authenticated', account: mapAccount(account) }
    setActiveSessionAccount(account.id)
  }

  return {
    state,
    bootstrap,
    initialize,
    login,
    logout,
    passwordChanged,
  }
}
