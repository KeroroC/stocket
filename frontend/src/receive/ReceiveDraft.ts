import type { DraftValue } from '../offline/DraftRepository'
import type { ScanResult } from '../scanner/Scanner'

export interface ReceiveItemSelection {
  id: string
  name: string
  version: number
  categoryId: string
  defaultInventoryType: 'BATCH' | 'ASSET'
}

export interface ReceiveLocationSelection {
  id: string
  name: string
  version: number
}

export interface ReceiveDraft extends DraftValue {
  createdAt: string
  item?: ReceiveItemSelection
  location?: ReceiveLocationSelection
  inventoryType: 'BATCH' | 'ASSET'
  quantity: string
  batchNumber: string
  assetNumber: string
  serialNumber: string
  productionDate: string
  expirationDate: string
  lastScan?: ScanResult
  idempotencyKey: string
}

export function newReceiveDraft(now = new Date()): ReceiveDraft {
  const timestamp = now.toISOString()
  return {
    id: crypto.randomUUID(),
    createdAt: timestamp,
    updatedAt: timestamp,
    inventoryType: 'BATCH',
    quantity: '1',
    batchNumber: '',
    assetNumber: '',
    serialNumber: '',
    productionDate: '',
    expirationDate: '',
    idempotencyKey: crypto.randomUUID(),
  }
}
