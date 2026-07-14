import 'fake-indexeddb/auto'
import { beforeEach, describe, expect, it } from 'vitest'
import { IndexedDbDraftRepository } from './IndexedDbDraftRepository'
import { MemoryDraftRepository } from './MemoryDraftRepository'

interface TestDraft {
  id: string
  updatedAt: string
  value: string
}

const day = 24 * 60 * 60 * 1000

describe.each([
  ['memory', () => new MemoryDraftRepository<TestDraft>()],
  ['indexeddb', () => new IndexedDbDraftRepository<TestDraft>(`draft-test-${crypto.randomUUID()}`)],
])('%s 草稿仓库', (_name, createRepository) => {
  let repository: ReturnType<typeof createRepository>

  beforeEach(() => {
    repository = createRepository()
  })

  it('按账号隔离保存、读取、列出和删除草稿', async () => {
    const draft = { id: 'draft-1', updatedAt: '2026-07-14T00:00:00.000Z', value: '牛奶' }
    await repository.save('account-a', draft)
    await repository.save('account-b', { ...draft, value: '咖啡' })

    expect(await repository.get('account-a', 'draft-1')).toMatchObject({ value: '牛奶' })
    expect(await repository.get('account-b', 'draft-1')).toMatchObject({ value: '咖啡' })
    expect(await repository.list('account-a')).toHaveLength(1)

    await repository.delete('account-a', 'draft-1')
    expect(await repository.get('account-a', 'draft-1')).toBeUndefined()
    expect(await repository.get('account-b', 'draft-1')).toBeDefined()
  })

  it('七天边界前保留草稿并清除已过期草稿', async () => {
    await repository.save('account-a', {
      id: 'draft-1',
      updatedAt: '2026-07-01T00:00:00.000Z',
      value: '临期草稿',
    })

    expect(await repository.purgeExpired(new Date(Date.parse('2026-07-01T00:00:00.000Z') + 7 * day - 1)))
      .toBe(0)
    expect(await repository.purgeExpired(new Date(Date.parse('2026-07-01T00:00:00.000Z') + 7 * day)))
      .toBe(1)
  })

  it('并发保存时保留 updatedAt 较新的版本', async () => {
    await Promise.all([
      repository.save('account-a', {
        id: 'draft-1', updatedAt: '2026-07-14T12:00:00.000Z', value: '新值',
      }),
      repository.save('account-a', {
        id: 'draft-1', updatedAt: '2026-07-14T11:00:00.000Z', value: '旧值',
      }),
    ])

    expect(await repository.get('account-a', 'draft-1')).toMatchObject({ value: '新值' })
  })
})
