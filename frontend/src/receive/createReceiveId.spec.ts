import { afterEach, describe, expect, it, vi } from 'vitest'
import { createReceiveId } from './createReceiveId'
import { newReceiveDraft } from './ReceiveDraft'

afterEach(() => vi.unstubAllGlobals())

describe('createReceiveId', () => {
  it('在不支持 crypto.randomUUID 的移动浏览器中生成 UUID v4', () => {
    vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array) => bytes.fill(0xab) })

    expect(createReceiveId()).toBe('abababab-abab-4bab-abab-abababababab')
    expect(newReceiveDraft()).toMatchObject({
      id: 'abababab-abab-4bab-abab-abababababab',
      idempotencyKey: 'abababab-abab-4bab-abab-abababababab',
    })
  })
})
