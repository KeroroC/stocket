import { apiRequest } from './http'
import type {
  InventoryAvailability,
  InventoryCommandResult,
  InventoryEntry,
  InventoryMovement,
  InventoryPage,
  ReceiveInventoryInput,
} from '../inventory/inventoryModels'

export interface InventoryFilters {
  itemId?: string
  locationId?: string
  type?: string
  assetStatus?: string
  expiresFrom?: string
  expiresTo?: string
  includeArchived?: boolean
  page?: number
  size?: number
}

export function listInventoryEntries(filters: InventoryFilters = {}) {
  const parameters = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') parameters.set(key, String(value))
  }
  const query = parameters.toString()
  return apiRequest<InventoryPage>(`/api/v1/inventory/entries${query ? `?${query}` : ''}`)
}

export const getInventoryEntry = (id: string) =>
  apiRequest<InventoryEntry>(`/api/v1/inventory/entries/${id}`)

export const getInventoryMovements = (id: string) =>
  apiRequest<InventoryMovement[]>(`/api/v1/inventory/entries/${id}/movements`)

export const getInventoryAvailability = (itemId: string) =>
  apiRequest<InventoryAvailability>(`/api/v1/inventory/availability?itemId=${encodeURIComponent(itemId)}`)

export const receiveInventory = (data: ReceiveInventoryInput, idempotencyKey: string) =>
  command('/api/v1/inventory/receipts', data, idempotencyKey)

export const consumeInventory = (entryId: string, data: { quantity: string }, idempotencyKey: string) =>
  command(`/api/v1/inventory/entries/${entryId}/consume`, data, idempotencyKey)

export const returnInventory = (entryId: string, data: { quantity: string; reason?: string }, idempotencyKey: string) =>
  command(`/api/v1/inventory/entries/${entryId}/return`, data, idempotencyKey)

export const adjustInventory = (entryId: string, data: { targetQuantity: string; reason: string }, idempotencyKey: string) =>
  command(`/api/v1/inventory/entries/${entryId}/adjust`, data, idempotencyKey)

export const transferInventory = (entryId: string, data: { targetLocationId: string; quantity: string }, idempotencyKey: string) =>
  command(`/api/v1/inventory/entries/${entryId}/transfer`, data, idempotencyKey)

export const markInventoryLost = (entryId: string, data: { reason: string }, idempotencyKey: string) =>
  command(`/api/v1/inventory/entries/${entryId}/lost`, data, idempotencyKey)

export const retireInventory = (entryId: string, data: { reason: string }, idempotencyKey: string) =>
  command(`/api/v1/inventory/entries/${entryId}/retire`, data, idempotencyKey)

function command(path: string, data: unknown, idempotencyKey: string) {
  return apiRequest<InventoryCommandResult>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(data),
  })
}
