import { apiRequest } from './http'

export interface NotificationChannel {
  id: string
  type: 'IN_APP' | 'WEB_PUSH' | 'SMTP' | 'WEBHOOK'
  enabled: boolean
  configuration: Record<string, unknown>
  hasSecret: boolean
  version: number
}

export interface ChannelInput {
  enabled: boolean
  configuration: Record<string, unknown>
  secret: string
  version: number
}

export interface PushSubscriptionInput {
  endpoint: string
  p256dh: string
  auth: string
}

export interface Delivery {
  id: string
  reminderId: string
  memberId: string
  channelType: string
  status: string
  attemptCount: number
  nextAttemptAt: string | null
  lastErrorCode: string | null
  lastErrorAt: string | null
  deliveredAt: string | null
  updatedAt: string
}

export interface DeliveryPage {
  content: Delivery[]
  page: number
  size: number
  total: number
}

export const listNotificationChannels = () =>
  apiRequest<NotificationChannel[]>('/api/v1/notification/channels')

export const updateNotificationChannel = (type: string, input: ChannelInput) =>
  apiRequest<NotificationChannel>(`/api/v1/notification/channels/${type}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  })

export const savePushSubscription = (input: PushSubscriptionInput) =>
  apiRequest<{ id: string; endpointSummary: string; enabled: boolean }>(
    '/api/v1/notification/push-subscription',
    { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input) },
  )

export const deletePushSubscription = () =>
  apiRequest<void>('/api/v1/notification/push-subscription', { method: 'DELETE' })

export const listFailedDeliveries = (page = 0, size = 20) =>
  apiRequest<DeliveryPage>(`/api/v1/admin/notification/deliveries?status=DEAD&page=${page}&size=${size}`)

export const retryDelivery = (id: string) =>
  apiRequest<Delivery>(`/api/v1/admin/notification/deliveries/${id}/retry`, { method: 'POST' })
