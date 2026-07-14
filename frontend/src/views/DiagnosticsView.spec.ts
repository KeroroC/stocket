import { render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import { getDiagnostics } from '../api/diagnostics'
import DiagnosticsView from './DiagnosticsView.vue'

vi.mock('../api/diagnostics', () => ({ getDiagnostics: vi.fn() }))

describe('DiagnosticsView', () => {
  it('uses text and fixed advice in addition to color', async () => {
    vi.mocked(getDiagnostics).mockResolvedValue({ checks: {
      database:{status:'OK',count:0,checkedAt:'2026-07-14T00:00:00Z',actionCode:'CHECK_DATABASE'},
      missingAttachments:{status:'WARN',count:2,checkedAt:'2026-07-14T00:00:00Z',actionCode:'REPAIR_ATTACHMENT_STORAGE'},
    } })
    render(DiagnosticsView)
    expect(await screen.findByText('正常')).toBeInTheDocument()
    expect(screen.getByText('需要处理')).toBeInTheDocument()
    expect(screen.getByText(/检查附件存储并运行恢复任务/)).toBeInTheDocument()
    expect(screen.queryByText(/Exception|\/tmp|localhost/)).not.toBeInTheDocument()
  })
})
