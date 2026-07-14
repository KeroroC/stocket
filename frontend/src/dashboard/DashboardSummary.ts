export interface DashboardSummary {
  expiring: number
  expired: number
  lowStock: number
  openTotal: number
}

export interface DashboardSearchItem {
  id: string
  name: string
  matchType: 'BARCODE_EXACT' | 'TEXT'
  totalAvailable: string
  locations: string[]
  earliestExpiration: string | null
  recentBatch: string | null
}

export interface DashboardResponse {
  summary: DashboardSummary
  search: DashboardSearchItem[]
}
