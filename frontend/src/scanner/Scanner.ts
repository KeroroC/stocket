export type ScanResult =
  | { kind: 'PRODUCT_BARCODE'; value: string }
  | { kind: 'LOCATION_CODE'; value: string }

export interface Scanner {
  start(video: HTMLVideoElement, onResult: (result: ScanResult) => void): Promise<void>
  stop(): Promise<void>
  setFacingMode?(mode: 'environment' | 'user'): Promise<void>
  toggleTorch?(enabled: boolean): Promise<void>
}
