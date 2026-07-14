import { fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import { listAuditLogs } from '../api/audit'
import AuditLogView from './AuditLogView.vue'

vi.mock('../api/audit', () => ({ listAuditLogs: vi.fn() }))

describe('AuditLogView', () => {
  it('filters, loads more, and exposes request id copy without dumping raw json', async () => {
    vi.mocked(listAuditLogs)
      .mockResolvedValueOnce({ items:[{id:'a1',occurredAt:'2026-07-14T00:00:00Z',eventType:'AttachmentUploaded',outcome:'SUCCESS',actorDisplayName:'管理员',subjectType:'ATTACHMENT',requestId:'request-123',source:'api',details:{purpose:'ITEM_IMAGE'}}], nextCursor:'next' })
      .mockResolvedValueOnce({ items:[{id:'a2',occurredAt:'2026-07-13T00:00:00Z',eventType:'InventoryReceived',outcome:'SUCCESS',subjectType:'INVENTORY_ENTRY',source:'api',details:{quantity:'2'}}] })
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
    render(AuditLogView)
    expect(await screen.findByText('AttachmentUploaded')).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name:'复制 request ID' }))
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('request-123')
    await fireEvent.click(screen.getByRole('button', { name:'加载更多' }))
    expect(await screen.findByText('InventoryReceived')).toBeInTheDocument()
    await fireEvent.update(screen.getByLabelText('Request ID'), 'trace-456')
    await fireEvent.click(screen.getByRole('button', { name:'筛选' }))
    await waitFor(() => expect(listAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({ requestId:'trace-456', size:50 })))
  })
})
