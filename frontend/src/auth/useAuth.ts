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
const SESSION_ACCOUNT_KEY = 'stocket:session-account'

function cacheAccount(account: CurrentAccount): void {
  sessionStorage.setItem(SESSION_ACCOUNT_KEY, JSON.stringify(account))
}

function readCachedAccount(): CurrentAccount | undefined {
  try {
    const value = sessionStorage.getItem(SESSION_ACCOUNT_KEY)
    return value ? JSON.parse(value) as CurrentAccount : undefined
  } catch {
    return undefined
  }
}

function clearCachedAccount(): void {
  sessionStorage.removeItem(SESSION_ACCOUNT_KEY)
}

export function useAuth() {
  const state = authState
  state.value = { kind: 'checking-setup' }
  setActiveSessionAccount(undefined)
  registerSessionExpiredHandler(() => {
    clearCachedAccount()
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
    let setupStatus
    try {
      setupStatus = await getSetupStatus()
      await refreshCsrf()
    } catch (cause) {
      const cached = readCachedAccount()
      if (navigator.onLine === false && cached) {
        state.value = { kind: 'authenticated', account: cached }
        setActiveSessionAccount(cached.id)
        return
      }
      state.value = { kind: 'anonymous' }
      return
    }

    if (!setupStatus.initialized) {
      state.value = { kind: 'setup-required' }
      return
    }

    try {
      const account = await getCurrentAccount()
      if (account.mustChangePassword) {
        state.value = { kind: 'password-change-required', account: mapAccount(account) }
      } else {
        const mapped = mapAccount(account)
        state.value = { kind: 'authenticated', account: mapped }
        cacheAccount(mapped)
        setActiveSessionAccount(account.id)
      }
    } catch {
      clearCachedAccount()
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
      const mapped = mapAccount(account)
      state.value = { kind: 'authenticated', account: mapped }
      cacheAccount(mapped)
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
      const mapped = mapAccount(account)
      state.value = { kind: 'authenticated', account: mapped }
      cacheAccount(mapped)
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
    clearCachedAccount()
    state.value = { kind: 'anonymous' }
  }

  async function passwordChanged(): Promise<void> {
    const account = await getCurrentAccount()
    const mapped = mapAccount(account)
    state.value = { kind: 'authenticated', account: mapped }
    cacheAccount(mapped)
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
