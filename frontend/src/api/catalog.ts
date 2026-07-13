import { apiRequest } from './http'
import type { CatalogSearchResult, CategoryNode, ItemDefinition, ItemInput } from '../catalog/catalogModels'

export interface CategoryInput { name: string; parentId?: string | null; defaultInventoryType: 'BATCH' | 'ASSET'; attributeSchema: CategoryNode['attributeSchema']; version?: number }
export const listCategories = (includeArchived = false) => apiRequest<CategoryNode[]>(`/api/v1/categories?includeArchived=${includeArchived}`)
export const createCategory = (data: CategoryInput) => json<CategoryNode>('/api/v1/categories', 'POST', data)
export const updateCategory = (id: string, data: CategoryInput) => json<CategoryNode>(`/api/v1/categories/${id}`, 'PATCH', data)
export const archiveCategory = (id: string, version: number) => apiRequest<CategoryNode>(`/api/v1/categories/${id}/archive?version=${version}`, { method: 'POST' })
export const restoreCategory = (id: string, version: number) => apiRequest<CategoryNode>(`/api/v1/categories/${id}/restore?version=${version}`, { method: 'POST' })
export const getItem = (id: string) => apiRequest<ItemDefinition>(`/api/v1/items/${id}`)
export const createItem = (data: ItemInput) => json<ItemDefinition>('/api/v1/items', 'POST', data)
export const updateItem = (id: string, data: ItemInput) => json<ItemDefinition>(`/api/v1/items/${id}`, 'PATCH', data)
export const archiveItem = (id: string, version: number) => apiRequest<ItemDefinition>(`/api/v1/items/${id}/archive?version=${version}`, { method: 'POST' })
export const restoreItem = (id: string, version: number) => apiRequest<ItemDefinition>(`/api/v1/items/${id}/restore?version=${version}`, { method: 'POST' })
export const searchCatalog = (q: string, init: RequestInit = {}, page = 0, size = 20, includeArchived = false) => apiRequest<CatalogSearchResult>(`/api/v1/catalog/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}&includeArchived=${includeArchived}`, init)

function json<T>(path: string, method: string, data: unknown) { return apiRequest<T>(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }) }
