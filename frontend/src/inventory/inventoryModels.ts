export type InventoryType = 'BATCH' | 'ASSET'
export type AssetStatus = 'AVAILABLE' | 'IN_USE' | 'LOANED' | 'LOST' | 'RETIRED'

export interface InventoryEntry {
  id: string
  itemId: string
  itemName: string
  locationId: string
  locationName: string
  type: InventoryType
  quantity: string
  receivedAt: string
  productionDate?: string | null
  expirationDate?: string | null
  customAttributes?: Record<string, unknown>
  version: number
  archived: boolean
  batchNumber?: string | null
  sourceEntryId?: string | null
  shelfLifeValue?: number | null
  shelfLifeUnit?: 'DAY' | 'MONTH' | 'YEAR' | null
  assetNumber?: string | null
  serialNumber?: string | null
  purchaseDate?: string | null
  warrantyExpiresOn?: string | null
  assetStatus?: AssetStatus | null
}

export interface InventoryPage {
  items: InventoryEntry[]
  page: number
  size: number
  total: number
}

export interface InventoryMovement {
  id: string
  type: string
  quantityDelta: string
  relatedEntryId?: string | null
  fromLocationId?: string | null
  toLocationId?: string | null
  reason?: string | null
  actorAccountId: string
  actorDisplayName: string
  requestId: string
  occurredAt: string
}

export interface InventoryAvailability {
  itemId: string
  totalAvailable: string
  earliestExpiration?: string | null
  activeEntryCount: number
}

export interface InventoryCommandResult {
  id: string
  type?: InventoryType
  quantity?: string
  locationId?: string
  expirationDate?: string | null
  assetStatus?: AssetStatus | null
  version?: number
  requestId?: string
}

export interface ReceiveInventoryInput {
  itemId: string
  type: InventoryType
  quantity: string
  locationId: string
  receivedAt: string
  productionDate?: string | null
  expirationDate?: string | null
  shelfLifeValue?: number | null
  shelfLifeUnit?: 'DAY' | 'MONTH' | 'YEAR' | null
  batchNumber?: string | null
  assetNumber?: string | null
  serialNumber?: string | null
  purchaseDate?: string | null
  warrantyExpiresOn?: string | null
  customAttributes: Record<string, unknown>
}
