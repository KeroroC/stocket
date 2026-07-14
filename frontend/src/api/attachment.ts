import { apiRequest, type ApiProblem } from './http'

export interface AttachmentSummary {
  id: string
  ownerType: 'ITEM_DEFINITION' | 'INVENTORY_ENTRY'
  ownerId: string
  purpose: 'COVER_IMAGE' | 'ITEM_IMAGE' | 'INVOICE' | 'WARRANTY'
  filename: string
  mediaType: string
  sizeBytes: number
  status: string
  createdAt: string
}

export interface UploadOptions {
  signal?: AbortSignal
  onProgress?: (progress: number) => void
}

export function listAttachments(ownerType: string, ownerId: string) {
  const query = new URLSearchParams({ ownerType, ownerId })
  return apiRequest<AttachmentSummary[]>(`/api/v1/attachments?${query}`)
}

export function deleteAttachment(id: string) {
  return apiRequest<void>(`/api/v1/attachments/${id}`, { method: 'DELETE' })
}

export function uploadAttachment(ownerType: string, ownerId: string, purpose: string, file: File, options: UploadOptions = {}) {
  return new Promise<AttachmentSummary>((resolve, reject) => {
    const query = new URLSearchParams({ ownerType, ownerId, purpose })
    const request = new XMLHttpRequest()
    request.open('POST', `/api/v1/attachments?${query}`)
    request.withCredentials = true
    const csrf = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1]
    if (csrf) request.setRequestHeader('X-XSRF-TOKEN', decodeURIComponent(csrf))
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) options.onProgress?.(Math.round((event.loaded / event.total) * 100))
    }
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) resolve(JSON.parse(request.responseText) as AttachmentSummary)
      else reject(problem(request))
    }
    request.onerror = () => reject({ status: 0, code: 'NETWORK_ERROR', retryable: true } satisfies ApiProblem)
    request.onabort = () => reject({ status: 0, code: 'UPLOAD_CANCELLED', retryable: true } satisfies ApiProblem)
    options.signal?.addEventListener('abort', () => request.abort(), { once: true })
    const form = new FormData(); form.append('file', file); request.send(form)
  })
}

export async function downloadAttachment(id: string): Promise<Blob> {
  const response = await fetch(`/api/v1/attachments/${id}/content`, { credentials: 'same-origin' })
  if (response.status === 401) {
    const { handleSessionExpired } = await import('../offline/sessionCleanup')
    await handleSessionExpired()
  }
  if (!response.ok) throw await responseProblem(response)
  return response.blob()
}

function problem(request: XMLHttpRequest): ApiProblem {
  try {
    const body = JSON.parse(request.responseText)
    return { status: request.status, code: body.code ?? 'UNKNOWN', detail: body.detail, retryable: body.retryable ?? false }
  } catch { return { status: request.status, code: 'UNKNOWN', retryable: false } }
}

async function responseProblem(response: Response): Promise<ApiProblem> {
  try {
    const body = await response.json()
    return { status: response.status, code: body.code ?? 'UNKNOWN', detail: body.detail, retryable: body.retryable ?? false }
  } catch { return { status: response.status, code: 'UNKNOWN', retryable: false } }
}
