export interface CurrentAccount {
  id: string
  username: string
  displayName: string
  role: string
}

export type AuthState =
  | { kind: 'checking-setup' }
  | { kind: 'setup-required' }
  | { kind: 'anonymous' }
  | { kind: 'password-change-required'; account: CurrentAccount }
  | { kind: 'authenticated'; account: CurrentAccount }
