import { apiRequest } from './http'

export interface AuditEntry {
  id: string; occurredAt: string; eventType: string; outcome: string
  actorAccountId?: string; actorDisplayName?: string; subjectType: string; subjectId?: string
  requestId?: string; source: string; details: Record<string, unknown>
}
export interface AuditPage { items: AuditEntry[]; nextCursor?: string }
export interface AuditFilters { from?: string; to?: string; actorId?: string; eventType?: string; outcome?: string; subjectType?: string; subjectId?: string; requestId?: string; cursor?: string; size?: number }

export function listAuditLogs(filters: AuditFilters = {}) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) if (value !== undefined && value !== '') query.set(key, String(value))
  return apiRequest<AuditPage>(`/api/v1/admin/audit-logs${query.size ? `?${query}` : ''}`)
}
