import { openDB, type DBSchema, type IDBPDatabase } from 'idb'
import { DRAFT_TTL_MS, type DraftRepository, type DraftValue } from './DraftRepository'

interface StoredDraft<T extends DraftValue> extends DraftValue {
  accountId: string
  expiresAt: number
  value: T
}

interface DraftDatabase<T extends DraftValue> extends DBSchema {
  drafts: {
    key: [string, string]
    value: StoredDraft<T>
    indexes: {
      accountId: string
      expiresAt: number
    }
  }
}

export class IndexedDbDraftRepository<T extends DraftValue> implements DraftRepository<T> {
  private readonly database: Promise<IDBPDatabase<DraftDatabase<T>>>

  constructor(databaseName = 'stocket-drafts-v1') {
    this.database = openDB<DraftDatabase<T>>(databaseName, 1, {
      upgrade(database) {
        const store = database.createObjectStore('drafts', { keyPath: ['accountId', 'id'] })
        store.createIndex('accountId', 'accountId')
        store.createIndex('expiresAt', 'expiresAt')
      },
    })
  }

  async save(accountId: string, draft: T): Promise<void> {
    const database = await this.database
    const transaction = database.transaction('drafts', 'readwrite')
    const key: [string, string] = [accountId, draft.id]
    const existing = await transaction.store.get(key)
    if (!existing || existing.updatedAt <= draft.updatedAt) {
      await transaction.store.put({
        accountId,
        id: draft.id,
        updatedAt: draft.updatedAt,
        expiresAt: Date.parse(draft.updatedAt) + DRAFT_TTL_MS,
        value: structuredClone(draft),
      })
    }
    await transaction.done
  }

  async get(accountId: string, draftId: string): Promise<T | undefined> {
    const value = await (await this.database).get('drafts', [accountId, draftId])
    return value ? structuredClone(value.value) : undefined
  }

  async list(accountId: string): Promise<T[]> {
    const values = await (await this.database).getAllFromIndex('drafts', 'accountId', accountId)
    return values.map((value) => structuredClone(value.value))
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
  }

  async delete(accountId: string, draftId: string): Promise<void> {
    await (await this.database).delete('drafts', [accountId, draftId])
  }

  async clearAccount(accountId: string): Promise<void> {
    const database = await this.database
    const transaction = database.transaction('drafts', 'readwrite')
    let cursor = await transaction.store.index('accountId').openKeyCursor(accountId)
    while (cursor) {
      await transaction.store.delete(cursor.primaryKey)
      cursor = await cursor.continue()
    }
    await transaction.done
  }

  async purgeExpired(now: Date): Promise<number> {
    const database = await this.database
    const transaction = database.transaction('drafts', 'readwrite')
    const range = IDBKeyRange.upperBound(now.getTime())
    let cursor = await transaction.store.index('expiresAt').openKeyCursor(range)
    let removed = 0
    while (cursor) {
      await transaction.store.delete(cursor.primaryKey)
      removed++
      cursor = await cursor.continue()
    }
    await transaction.done
    return removed
  }
}
