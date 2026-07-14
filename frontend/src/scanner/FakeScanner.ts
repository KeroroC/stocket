import type { Scanner, ScanResult } from './Scanner'
import { parseScanPayload } from './scanPayload'

export class FakeScanner implements Scanner {
  private onResult?: (result: ScanResult) => void
  private lastValue?: string
  private lastAt = Number.NEGATIVE_INFINITY

  async start(_video: HTMLVideoElement, onResult: (result: ScanResult) => void): Promise<void> {
    this.onResult = onResult
  }

  async stop(): Promise<void> {
    this.onResult = undefined
  }

  emit(raw: string, at = Date.now()): void {
    const result = parseScanPayload(raw)
    if (!result || !this.onResult) return
    const key = `${result.kind}:${result.value}`
    if (key === this.lastValue && at - this.lastAt < 1500) return
    this.lastValue = key
    this.lastAt = at
    this.onResult(result)
  }
}
