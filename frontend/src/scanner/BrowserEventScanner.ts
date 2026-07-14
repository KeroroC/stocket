import type { Scanner, ScanResult } from './Scanner'
import { parseScanPayload } from './scanPayload'

export class BrowserEventScanner implements Scanner {
  private listener?: (event: Event) => void

  async start(_video: HTMLVideoElement, onResult: (result: ScanResult) => void): Promise<void> {
    await this.stop()
    this.listener = (event) => {
      const result = parseScanPayload(String((event as CustomEvent).detail ?? ''))
      if (result) onResult(result)
    }
    window.addEventListener('stocket:test-scan', this.listener)
  }

  async stop(): Promise<void> {
    if (this.listener) window.removeEventListener('stocket:test-scan', this.listener)
    this.listener = undefined
  }
}
