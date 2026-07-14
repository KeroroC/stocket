import type { ScanResult } from './Scanner'

export function parseScanPayload(raw: string): ScanResult | undefined {
  const value = raw.trim()
  if (!value) return undefined
  const locationPrefix = 'stocket:location:'
  if (value.toLowerCase().startsWith(locationPrefix)) {
    const code = value.slice(locationPrefix.length).trim()
    return code ? { kind: 'LOCATION_CODE', value: code } : undefined
  }
  if (value.toLowerCase().startsWith('stocket:')) return undefined
  return { kind: 'PRODUCT_BARCODE', value: value.toUpperCase() }
}
