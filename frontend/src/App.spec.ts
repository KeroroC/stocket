import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

const stylesheet = readFileSync(
  resolve(process.cwd(), 'src/styles/main.css'),
  'utf8',
)

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('展示家庭资产工程基础状态并连接系统 API', async () => {
    const fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ name: 'stocket', version: '0.1.0-test' }),
    })
    vi.stubGlobal('fetch', fetch)

    render(App)

    expect(
      screen.getByRole('heading', { name: '家庭资产' }),
    ).toBeInTheDocument()
    expect(screen.getByText('工程基础已就绪')).toBeInTheDocument()
    const status = await screen.findByText('后端 0.1.0-test 已连接')

    expect(status).toBeInTheDocument()
    expect(status.closest('.el-tag--success')).not.toBeNull()
    expect(fetch).toHaveBeenCalledExactlyOnceWith('/api/v1/system', {
      credentials: 'same-origin',
    })
  })

  it('系统 API 不可用时展示警告状态', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 503 }),
    )

    render(App)

    const status = await screen.findByText('后端暂不可用')

    expect(status.closest('.el-tag--warning')).not.toBeNull()
  })

  it('将动态连接状态作为礼貌播报的完整状态消息', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))

    render(App)

    const status = screen.getByRole('status')

    expect(status).toHaveAttribute('aria-live', 'polite')
    expect(status).toHaveAttribute('aria-atomic', 'true')
  })

  it('为动态基础状态提供可访问的成功与警告样式', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))

    render(App)

    const status = screen
      .getByText('正在连接后端…')
      .closest('.foundation-status')

    expect(status).not.toBeNull()
    expect(screen.getByText('正在连接后端…').closest('.el-tag--warning')).not.toBeNull()

    expect(stylesheet).toContain('.foundation-status.el-tag--success')
    expect(stylesheet).toContain('#166534')
    expect(stylesheet).toContain('.foundation-status.el-tag--warning')
    expect(stylesheet).toContain('#92400e')
  })
})
