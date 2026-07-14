import { describe, expect, it, vi } from 'vitest'
import { FakeScanner } from './FakeScanner'
import { ScannerError, ZxingScanner } from './ZxingScanner'

describe('扫描器生命周期', () => {
  it('1500ms 内忽略重复帧', async () => {
    const scanner = new FakeScanner()
    const onResult = vi.fn()
    await scanner.start(document.createElement('video'), onResult)

    scanner.emit('milk-01', 1000)
    scanner.emit('milk-01', 2000)
    scanner.emit('milk-01', 2501)

    expect(onResult).toHaveBeenCalledTimes(2)
  })

  it('停止时释放 reader controls 和全部 MediaStream tracks', async () => {
    const stop = vi.fn()
    const trackStop = vi.fn()
    const video = document.createElement('video')
    Object.defineProperty(video, 'srcObject', {
      configurable: true,
      value: { getTracks: () => [{ stop: trackStop }] },
      writable: true,
    })
    const reader = {
      decodeFromConstraints: vi.fn().mockResolvedValue({ stop }),
    }
    const scanner = new ZxingScanner(reader as never)

    await scanner.start(video, vi.fn())
    await scanner.stop()

    expect(stop).toHaveBeenCalledOnce()
    expect(trackStop).toHaveBeenCalledOnce()
    expect(video.srcObject).toBeNull()
  })

  it('把权限拒绝和无摄像头转换为稳定错误', async () => {
    const denied = new ZxingScanner({
      decodeFromConstraints: vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError')),
    } as never)
    await expect(denied.start(document.createElement('video'), vi.fn()))
      .rejects.toMatchObject({ code: 'CAMERA_PERMISSION_DENIED' } satisfies Partial<ScannerError>)

    const missing = new ZxingScanner({
      decodeFromConstraints: vi.fn().mockRejectedValue(new DOMException('missing', 'NotFoundError')),
    } as never)
    await expect(missing.start(document.createElement('video'), vi.fn()))
      .rejects.toMatchObject({ code: 'CAMERA_NOT_FOUND' } satisfies Partial<ScannerError>)
  })
})
