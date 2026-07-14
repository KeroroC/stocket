export type ExportKind = 'catalog' | 'inventory'

export async function downloadCsv(kind: ExportKind, filters: Record<string, unknown> = {}): Promise<Blob> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) if (value !== undefined && value !== '' && value !== false) query.set(key, String(value))
  const response = await fetch(`/api/v1/exports/${kind}.csv${query.size ? `?${query}` : ''}`, { credentials: 'same-origin' })
  if (!response.ok) {
    const body = await response.json().catch(() => ({}))
    throw { status: response.status, code: body.code ?? 'EXPORT_FAILED', detail: body.detail, retryable: false }
  }
  return response.blob()
}
