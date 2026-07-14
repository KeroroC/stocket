import { DRAFT_TTL_MS, type DraftRepository, type DraftValue } from './DraftRepository'

interface StoredDraft<T> {
  accountId: string
  draft: T
  expiresAt: number
}

export class MemoryDraftRepository<T extends DraftValue> implements DraftRepository<T> {
  private readonly values = new Map<string, StoredDraft<T>>()

  async save(accountId: string, draft: T): Promise<void> {
    const key = this.key(accountId, draft.id)
    const existing = this.values.get(key)
    if (existing && existing.draft.updatedAt > draft.updatedAt) return
    this.values.set(key, {
      accountId,
      draft: structuredClone(draft),
      expiresAt: Date.parse(draft.updatedAt) + DRAFT_TTL_MS,
    })
  }

  async get(accountId: string, draftId: string): Promise<T | undefined> {
    const value = this.values.get(this.key(accountId, draftId))
    return value ? structuredClone(value.draft) : undefined
  }

  async list(accountId: string): Promise<T[]> {
    return [...this.values.values()]
      .filter((value) => value.accountId === accountId)
      .map((value) => structuredClone(value.draft))
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
  }

  async delete(accountId: string, draftId: string): Promise<void> {
    this.values.delete(this.key(accountId, draftId))
  }

  async clearAccount(accountId: string): Promise<void> {
    for (const [key, value] of this.values) {
      if (value.accountId === accountId) this.values.delete(key)
    }
  }

  async purgeExpired(now: Date): Promise<number> {
    let removed = 0
    for (const [key, value] of this.values) {
      if (value.expiresAt <= now.getTime()) {
        this.values.delete(key)
        removed++
      }
    }
    return removed
  }

  private key(accountId: string, draftId: string) {
    return `${accountId}\0${draftId}`
  }
}
