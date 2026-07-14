import { BrowserMultiFormatReader } from '@zxing/browser'
import type { Scanner, ScanResult } from './Scanner'
import { parseScanPayload } from './scanPayload'

interface ReaderResult { getText(): string }
interface ReaderControls { stop(): void; switchTorch?: (enabled: boolean) => Promise<void> }
interface ReaderAdapter {
  decodeFromConstraints(
    constraints: MediaStreamConstraints,
    video: HTMLVideoElement,
    callback: (result?: ReaderResult) => void,
  ): Promise<ReaderControls>
}

export class ScannerError extends Error {
  constructor(public readonly code: 'CAMERA_PERMISSION_DENIED' | 'CAMERA_NOT_FOUND' | 'CAMERA_UNAVAILABLE') {
    super(code)
  }
}

export class ZxingScanner implements Scanner {
  private controls?: ReaderControls
  private video?: HTMLVideoElement
  private callback?: (result: ScanResult) => void
  private facingMode: 'environment' | 'user' = 'environment'
  private lastValue?: string
  private lastAt = Number.NEGATIVE_INFINITY

  constructor(private readonly reader: ReaderAdapter = new BrowserMultiFormatReader() as ReaderAdapter) {}

  async start(video: HTMLVideoElement, onResult: (result: ScanResult) => void): Promise<void> {
    await this.stop()
    this.video = video
    this.callback = onResult
    try {
      this.controls = await this.reader.decodeFromConstraints(
        { video: { facingMode: { ideal: this.facingMode } }, audio: false },
        video,
        (result) => {
          if (result) this.accept(result.getText())
        },
      )
    } catch (error) {
      throw this.mapError(error)
    }
  }

  async stop(): Promise<void> {
    this.controls?.stop()
    this.controls = undefined
    const stream = this.video?.srcObject
    if (stream && 'getTracks' in stream) {
      stream.getTracks().forEach((track) => track.stop())
    }
    if (this.video) this.video.srcObject = null
    this.video = undefined
    this.callback = undefined
  }

  async setFacingMode(mode: 'environment' | 'user'): Promise<void> {
    this.facingMode = mode
    const video = this.video
    const callback = this.callback
    if (video && callback) await this.start(video, callback)
  }

  async toggleTorch(enabled: boolean): Promise<void> {
    if (!this.controls?.switchTorch) throw new ScannerError('CAMERA_UNAVAILABLE')
    await this.controls.switchTorch(enabled)
  }

  private accept(raw: string): void {
    const result = parseScanPayload(raw)
    if (!result || !this.callback) return
    const now = Date.now()
    const key = `${result.kind}:${result.value}`
    if (key === this.lastValue && now - this.lastAt < 1500) return
    this.lastValue = key
    this.lastAt = now
    this.callback(result)
  }

  private mapError(error: unknown): ScannerError {
    if (error instanceof DOMException && error.name === 'NotAllowedError') {
      return new ScannerError('CAMERA_PERMISSION_DENIED')
    }
    if (error instanceof DOMException && error.name === 'NotFoundError') {
      return new ScannerError('CAMERA_NOT_FOUND')
    }
    return new ScannerError('CAMERA_UNAVAILABLE')
  }
}
