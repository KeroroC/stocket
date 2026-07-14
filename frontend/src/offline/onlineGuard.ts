import type { ApiProblem } from '../api/http'

export function ensureOnlineForMutation(): void {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    throw {
      status: 0,
      code: 'OFFLINE_WRITE_BLOCKED',
      detail: '离线状态下不能提交库存变更，请联网后重试。',
      retryable: true,
    } satisfies ApiProblem
  }
}
