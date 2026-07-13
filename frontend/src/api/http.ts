export interface ApiProblem {
  status: number
  code: string
  detail?: string
  retryable: boolean
  fieldErrors?: Array<{ field: string; message: string }>
}

function getCookie(name: string): string | undefined {
  const match = document.cookie.match(
    new RegExp(`(?:^|; )${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}=([^;]*)`),
  )
  return match?.[1] ? decodeURIComponent(match[1]) : undefined
}

function isSafeMethod(method: string): boolean {
  return method === 'GET' || method === 'HEAD'
}

function buildHeaders(init: RequestInit): Record<string, string> {
  const headers: Record<string, string> = {}

  if (init.headers) {
    if (init.headers instanceof Headers) {
      init.headers.forEach((value, key) => {
        headers[key] = value
      })
    } else if (Array.isArray(init.headers)) {
      for (const [key, value] of init.headers) {
        headers[key] = value
      }
    } else {
      Object.assign(headers, init.headers)
    }
  }

  const method = (init.method ?? 'GET').toUpperCase()
  if (!isSafeMethod(method)) {
    const csrfToken = getCookie('XSRF-TOKEN')
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken
    }
  }

  return headers
}

async function parseProblem(response: Response): Promise<ApiProblem> {
  try {
    const body = await response.json()
    return {
      status: response.status,
      code: body.code ?? body.error ?? body.type ?? 'UNKNOWN',
      detail: body.detail ?? body.message,
      retryable: body.retryable ?? false,
      fieldErrors: body.fieldErrors,
    }
  } catch {
    return {
      status: response.status,
      code: 'UNKNOWN',
      retryable: false,
    }
  }
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
  retryCsrf = true,
): Promise<T> {
  const url = path.startsWith('/') ? path : `/${path}`
  const method = (init.method ?? 'GET').toUpperCase()

  const response = await fetch(url, {
    ...init,
    credentials: 'same-origin',
    headers: buildHeaders(init),
  })

  if (response.status === 403 && retryCsrf && !isSafeMethod(method)) {
    const problem = await parseProblem(response)
    const { refreshCsrf } = await import('./identity')
    await refreshCsrf()
    return apiRequest<T>(path, { ...init, method }, false)
  }

  if (!response.ok) {
    throw await parseProblem(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const contentLength = response.headers?.get('Content-Length')
  if (contentLength === '0') {
    return undefined as T
  }

  return (await response.json()) as T
}
