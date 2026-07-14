import { apiRequest } from './http'
import type { DashboardResponse } from '../dashboard/DashboardSummary'

export const getDashboard = (query = '', signal?: AbortSignal) =>
  apiRequest<DashboardResponse>(`/api/v1/dashboard?q=${encodeURIComponent(query)}`, { signal })
