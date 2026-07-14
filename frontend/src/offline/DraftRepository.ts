export interface DraftValue {
  id: string
  updatedAt: string
}

export interface DraftRepository<T extends DraftValue> {
  save(accountId: string, draft: T): Promise<void>
  get(accountId: string, draftId: string): Promise<T | undefined>
  list(accountId: string): Promise<T[]>
  delete(accountId: string, draftId: string): Promise<void>
  clearAccount(accountId: string): Promise<void>
  purgeExpired(now: Date): Promise<number>
}

export const DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000
