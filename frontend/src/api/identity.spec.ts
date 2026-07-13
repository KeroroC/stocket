import { afterEach, describe, expect, it, vi } from 'vitest'
import { revokeInvite } from './identity'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('invite API contract', () => {
  it('uses the backend revoke route and method', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, { status: 204 }),
    )

    await revokeInvite('invite-1')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/admin/invites/invite-1/revoke',
      expect.objectContaining({ method: 'POST' }),
    )
  })
})
