import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDashboard } from '../api/dashboard'
import HomeView from './HomeView.vue'

vi.mock('../api/dashboard', () => ({ getDashboard: vi.fn() }))
afterEach(cleanup)

describe('HomeView', () => {
  it('按任务顺序展示搜索、快捷入库和提醒摘要', async () => {
    vi.mocked(getDashboard).mockResolvedValue({
      summary: { expiring: 2, expired: 1, lowStock: 3, openTotal: 6 },
      search: [],
    })
    render(HomeView)

    expect(await screen.findByRole('searchbox', { name: '全局搜索' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '快捷入库' })).toBeInTheDocument()
    const labels = screen.getAllByRole('heading', { level: 2 }).map((node) => node.textContent)
    expect(labels).toEqual(['30 天内到期', '已过期', '低库存', '待处理项目'])
  })

  it('搜索结果显示库存、位置、最近批次和最早过期', async () => {
    vi.mocked(getDashboard)
      .mockResolvedValueOnce({ summary: { expiring: 0, expired: 0, lowStock: 0, openTotal: 0 }, search: [] })
      .mockResolvedValueOnce({
        summary: { expiring: 0, expired: 0, lowStock: 0, openTotal: 0 },
        search: [{ id: 'item-1', name: '牛奶', matchType: 'BARCODE_EXACT', totalAvailable: '3', locations: ['冰箱'], earliestExpiration: '2026-07-20', recentBatch: 'B-01' }],
      })
    render(HomeView)
    await screen.findByRole('searchbox', { name: '全局搜索' })
    await fireEvent.update(screen.getByRole('searchbox', { name: '全局搜索' }), '690001')

    expect(await screen.findByText('牛奶')).toBeInTheDocument()
    expect(screen.getByText(/总量 3/)).toBeInTheDocument()
    expect(screen.getByText(/冰箱/)).toBeInTheDocument()
    expect(screen.getByText(/B-01/)).toBeInTheDocument()
    expect(screen.getByText(/2026-07-20/)).toBeInTheDocument()
  })
})
