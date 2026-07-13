export type AttributeType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'ENUM'
export interface AttributeDefinition { key: string; label: string; type: AttributeType; required: boolean; defaultValue: unknown; options: string[]; order: number }
export interface CategoryNode { id: string; parentId: string | null; name: string; defaultInventoryType: 'BATCH' | 'ASSET'; attributeSchema: AttributeDefinition[]; version: number; archived: boolean }
export interface ItemDefinition { id: string; name: string; categoryId: string; brand: string | null; model: string | null; specification: string | null; defaultUnit: string; defaultShelfLifeValue: number | null; defaultShelfLifeUnit: 'DAY' | 'MONTH' | 'YEAR' | null; customAttributes: Record<string, unknown>; barcodes: string[]; tags: string[]; version: number; archived: boolean }
export interface ItemInput extends Omit<ItemDefinition, 'id' | 'archived' | 'version'> { version?: number }
export interface CatalogSearchItem { id: string; name: string; categoryPath: string | null; brand: string | null; model: string | null; specification: string | null; tags: string[]; barcodes: string[]; matchType: 'BARCODE_EXACT' | 'TEXT' }
export interface CatalogSearchResult { items: CatalogSearchItem[]; page: number; size: number; total: number }
