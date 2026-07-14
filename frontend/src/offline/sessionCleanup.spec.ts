import { describe, expect, it, vi } from 'vitest'
import { MemoryDraftRepository } from './MemoryDraftRepository'
import { createSessionCleanup } from './sessionCleanup'

interface Draft { id: string; updatedAt: string; value: string }

describe('会话数据清理', () => {
  it('登出只清除当前账号草稿、查询快照和 scanner 状态', async () => {
    const drafts = new MemoryDraftRepository<Draft>()
    await drafts.save('account-a', { id: 'a', updatedAt: new Date().toISOString(), value: 'A' })
    await drafts.save('account-b', { id: 'b', updatedAt: new Date().toISOString(), value: 'B' })
    const clearQueries = vi.fn()
    const stopScanner = vi.fn().mockResolvedValue(undefined)
    const cleanup = createSessionCleanup(drafts, clearQueries, stopScanner)

    await cleanup.clearAccount('account-a')

    expect(await drafts.list('account-a')).toEqual([])
    expect(await drafts.list('account-b')).toHaveLength(1)
    expect(clearQueries).toHaveBeenCalledOnce()
    expect(stopScanner).toHaveBeenCalledOnce()
  })
})
