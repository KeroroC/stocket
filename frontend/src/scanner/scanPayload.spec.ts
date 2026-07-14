import { describe, expect, it } from 'vitest'
import { parseScanPayload } from './scanPayload'

describe('扫码载荷解析', () => {
  it('规范化商品条码', () => {
    expect(parseScanPayload('  ab-123  ')).toEqual({ kind: 'PRODUCT_BARCODE', value: 'AB-123' })
  })

  it('解析位置二维码', () => {
    expect(parseScanPayload('stocket:location:kitchen-01')).toEqual({
      kind: 'LOCATION_CODE', value: 'kitchen-01',
    })
  })

  it('拒绝空值和未知 stocket 载荷', () => {
    expect(parseScanPayload('  ')).toBeUndefined()
    expect(parseScanPayload('stocket:member:abc')).toBeUndefined()
  })
})
