import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../api/http'
import { ensureOnlineForMutation } from './onlineGuard'

afterEach(() => vi.unstubAllGlobals())

describe('在线写入门禁', () => {
  it('离线时返回稳定客户端错误且不发起 mutation 请求', async () => {
    vi.stubGlobal('navigator', { onLine: false })
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)

    expect(() => ensureOnlineForMutation()).toThrow(expect.objectContaining({
      code: 'OFFLINE_WRITE_BLOCKED',
      retryable: true,
    }))
    await expect(apiRequest('/api/v1/inventory/receipts', {
      method: 'POST',
      mutation: true,
    })).rejects.toMatchObject({ code: 'OFFLINE_WRITE_BLOCKED' })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('离线不影响本地草稿逻辑或只读请求', async () => {
    vi.stubGlobal('navigator', { onLine: false })
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => ({ ok: true }),
    })
    vi.stubGlobal('fetch', fetch)

    await expect(apiRequest<{ ok: boolean }>('/api/v1/items')).resolves.toEqual({ ok: true })
  })
})
