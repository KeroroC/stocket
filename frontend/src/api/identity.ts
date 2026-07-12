import { apiRequest } from './http'

// --- Response types ---

export interface SetupStatusResponse {
  initialized: boolean
}

export interface CurrentAccountResponse {
  accountId: string
  username: string
  role: string
}

export interface AccountResponse {
  id: string
  username: string
  displayName: string
  email: string | null
  role: string
  mustChangePassword: boolean
}

// --- Request types ---

export interface InitializeRequest {
  householdName: string
  timezone: string
  username: string
  displayName: string
  password: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

// --- API functions ---

export function getSetupStatus(): Promise<SetupStatusResponse> {
  return apiRequest<SetupStatusResponse>('/api/v1/setup/status')
}

export function initialize(data: InitializeRequest): Promise<CurrentAccountResponse> {
  return apiRequest<CurrentAccountResponse>('/api/v1/setup/initialize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function refreshCsrf(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/csrf', {}, false)
}

export function login(data: LoginRequest): Promise<CurrentAccountResponse> {
  return apiRequest<CurrentAccountResponse>('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function logout(): Promise<void> {
  return apiRequest<void>('/api/v1/auth/logout', { method: 'POST' })
}

export function getCurrentAccount(): Promise<AccountResponse> {
  return apiRequest<AccountResponse>('/api/v1/account')
}

export function changePassword(data: ChangePasswordRequest): Promise<void> {
  return apiRequest<void>('/api/v1/account/password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}
