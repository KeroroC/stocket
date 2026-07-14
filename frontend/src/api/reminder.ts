import { apiRequest } from './http'

export interface Reminder {
  id: string
  itemId: string
  inventoryEntryId: string | null
  itemName: string
  locationName: string | null
  availableQuantity: string | null
  type: 'EXPIRING' | 'EXPIRED' | 'LOW_STOCK' | 'INTEGRITY'
  triggerKey: string
  triggerAt: string
  status: 'SCHEDULED' | 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'
  version: number
}

export interface ReminderPage {
  content: Reminder[]
  page: number
  size: number
  total: number
}

export interface ReminderFilters {
  status?: string
  type?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export function listReminders(filters: ReminderFilters = {}) {
  const parameters = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined && value !== '') parameters.set(key, String(value))
  }
  const query = parameters.toString()
  return apiRequest<ReminderPage>(`/api/v1/reminders${query ? `?${query}` : ''}`)
}

export const acknowledgeReminder = (id: string) =>
  apiRequest<Reminder>(`/api/v1/reminders/${id}/acknowledge`, { method: 'POST' })
