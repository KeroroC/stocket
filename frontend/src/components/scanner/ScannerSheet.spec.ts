import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { FakeScanner } from '../../scanner/FakeScanner'
import ScannerSheet from './ScannerSheet.vue'

afterEach(cleanup)

describe('ScannerSheet', () => {
  it('打开后启动，关闭和页面隐藏时停止摄像头', async () => {
    const scanner = new FakeScanner()
    const start = vi.spyOn(scanner, 'start')
    const stop = vi.spyOn(scanner, 'stop')
    const { rerender } = render(ScannerSheet, { props: { modelValue: true, scanner } })

    await waitFor(() => expect(start).toHaveBeenCalledOnce())
    await rerender({ modelValue: false, scanner })
    expect(stop).toHaveBeenCalled()

    await rerender({ modelValue: true, scanner })
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    await waitFor(() => expect(stop.mock.calls.length).toBeGreaterThanOrEqual(2))
  })

  it('提供手工输入后备入口并返回规范化结果', async () => {
    const scanner = new FakeScanner()
    const { emitted } = render(ScannerSheet, { props: { modelValue: true, scanner } })

    await fireEvent.update(screen.getByLabelText('手工输入条码或位置码'), ' abc-1 ')
    await fireEvent.click(screen.getByRole('button', { name: '使用手工输入' }))

    expect(emitted().result?.[0]).toEqual([{ kind: 'PRODUCT_BARCODE', value: 'ABC-1' }])
  })
})
