import type { DraftRepository, DraftValue } from './DraftRepository'

export function createSessionCleanup<T extends DraftValue>(
  drafts: DraftRepository<T>,
  clearQueries: () => void | Promise<void> = () => undefined,
  stopScanner: () => void | Promise<void> = () => undefined,
) {
  return {
    async clearAccount(accountId: string): Promise<void> {
      await Promise.all([
        drafts.clearAccount(accountId),
        Promise.resolve(clearQueries()),
        Promise.resolve(stopScanner()),
      ])
    },
  }
}

let activeAccountId: string | undefined
let clearAccountHandler: (accountId: string) => Promise<void> = async (accountId) => {
  if (typeof indexedDB === 'undefined') return
  const { IndexedDbDraftRepository } = await import('./IndexedDbDraftRepository')
  await new IndexedDbDraftRepository().clearAccount(accountId)
}
let sessionExpiredHandler: () => void = () => undefined

export function registerSessionCleanup(handler: (accountId: string) => Promise<void>): void {
  clearAccountHandler = handler
}

export function setActiveSessionAccount(accountId: string | undefined): void {
  activeAccountId = accountId
}

export function registerSessionExpiredHandler(handler: () => void): void {
  sessionExpiredHandler = handler
}

export async function clearActiveSessionData(): Promise<void> {
  const accountId = activeAccountId
  activeAccountId = undefined
  if (accountId) await clearAccountHandler(accountId)
}

export async function handleSessionExpired(): Promise<void> {
  await clearActiveSessionData()
  sessionExpiredHandler()
}
