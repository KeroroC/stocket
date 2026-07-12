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

export interface InviteStatusResponse {
  available: boolean
  role: string
  expiresAt: string
}

export interface AcceptInviteRequest {
  username: string
  displayName: string
  password: string
}

export interface AcceptInviteResponse {
  accountId: string
  memberId: string
}

// --- Account & session types ---

export interface UpdateProfileRequest {
  displayName: string
  email?: string | null
}

export interface SessionInfo {
  id: string
  createdAt: string
  lastSeenAt: string
  userAgent: string | null
  current: boolean
}

// --- Member types ---

export interface MemberInfo {
  id: string
  accountId: string
  username: string
  displayName: string
  role: string
  enabled: boolean
  createdAt: string
}

export interface CreateMemberRequest {
  username: string
  displayName: string
  role: string
}

export interface CreateMemberResponse {
  memberId: string
  accountId: string
  temporaryPassword: string
}

export interface UpdateMemberRequest {
  role?: string
  displayName?: string
  enabled?: boolean
}

export interface ResetPasswordResponse {
  temporaryPassword: string
}

// --- Invite types ---

export interface InviteListItem {
  id: string
  role: string
  expiresAt: string
  status: string
  createdAt: string
}

export interface CreateInviteRequest {
  role: string
  expiresInHours?: number
}

export interface CreateInviteResponse {
  inviteId: string
  inviteLink: string
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

export function getInviteStatus(token: string): Promise<InviteStatusResponse> {
  return apiRequest<InviteStatusResponse>(`/api/v1/invites/${token}/status`)
}

export function acceptInvite(token: string, data: AcceptInviteRequest): Promise<AcceptInviteResponse> {
  return apiRequest<AcceptInviteResponse>(`/api/v1/invites/${token}/accept`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

// --- Account & session functions ---

export function updateProfile(data: UpdateProfileRequest): Promise<AccountResponse> {
  return apiRequest<AccountResponse>('/api/v1/account/profile', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function getSessions(): Promise<SessionInfo[]> {
  return apiRequest<SessionInfo[]>('/api/v1/account/sessions')
}

export function revokeSession(sessionId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/account/sessions/${sessionId}`, { method: 'DELETE' })
}

export function revokeOtherSessions(): Promise<void> {
  return apiRequest<void>('/api/v1/account/sessions/others', { method: 'DELETE' })
}

// --- Admin member functions ---

export function getMembers(): Promise<MemberInfo[]> {
  return apiRequest<MemberInfo[]>('/api/v1/admin/members')
}

export function createMember(data: CreateMemberRequest): Promise<CreateMemberResponse> {
  return apiRequest<CreateMemberResponse>('/api/v1/admin/members', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function updateMember(memberId: string, data: UpdateMemberRequest): Promise<MemberInfo> {
  return apiRequest<MemberInfo>(`/api/v1/admin/members/${memberId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function resetMemberPassword(memberId: string): Promise<ResetPasswordResponse> {
  return apiRequest<ResetPasswordResponse>(`/api/v1/admin/members/${memberId}/password-reset`, {
    method: 'POST',
  })
}

// --- Admin invite functions ---

export function getInvites(): Promise<InviteListItem[]> {
  return apiRequest<InviteListItem[]>('/api/v1/admin/invites')
}

export function createInvite(data: CreateInviteRequest): Promise<CreateInviteResponse> {
  return apiRequest<CreateInviteResponse>('/api/v1/admin/invites', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

export function revokeInvite(inviteId: string): Promise<void> {
  return apiRequest<void>(`/api/v1/admin/invites/${inviteId}`, { method: 'DELETE' })
}
