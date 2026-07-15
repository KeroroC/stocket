export type ScanResult =
  | { kind: 'PRODUCT_BARCODE'; value: string }
  | { kind: 'LOCATION_CODE'; value: string }

export interface Scanner {
  start(video: HTMLVideoElement, onResult: (result: ScanResult) => void): Promise<void>
  stop(): Promise<void>
  availabilityError?(): ScannerAvailabilityError | undefined
  setFacingMode?(mode: 'environment' | 'user'): Promise<void>
  toggleTorch?(enabled: boolean): Promise<void>
}

export type ScannerAvailabilityError =
  | 'CAMERA_PERMISSION_DENIED'
  | 'CAMERA_NOT_FOUND'
  | 'CAMERA_INSECURE_CONTEXT'
  | 'CAMERA_UNSUPPORTED'
  | 'CAMERA_UNAVAILABLE'
