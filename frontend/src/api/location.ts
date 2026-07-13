import { apiRequest } from './http'

export interface LocationNode { id: string; parentId: string | null; name: string; fullPath: string; publicCode: string; version: number; archived: boolean }
export interface LocationInput { name: string; parentId?: string | null; version?: number }
export const listLocations = (includeArchived = false) => apiRequest<LocationNode[]>(`/api/v1/locations?includeArchived=${includeArchived}`)
export const createLocation = (data: LocationInput) => json<LocationNode>('/api/v1/locations', 'POST', data)
export const updateLocation = (id: string, data: LocationInput) => json<LocationNode>(`/api/v1/locations/${id}`, 'PATCH', data)
export const archiveLocation = (id: string, version: number) => apiRequest<LocationNode>(`/api/v1/locations/${id}/archive?version=${version}`, { method: 'POST' })
export const restoreLocation = (id: string, version: number) => apiRequest<LocationNode>(`/api/v1/locations/${id}/restore?version=${version}`, { method: 'POST' })
export const resolveLocationCode = (payload: string) => json<LocationNode>('/api/v1/locations/resolve-code', 'POST', { payload })
function json<T>(path: string, method: string, data: unknown) { return apiRequest<T>(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }) }
