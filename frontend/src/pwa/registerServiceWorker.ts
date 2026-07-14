import { registerSW } from 'virtual:pwa-register'
import { waitForDraftWrites } from './updateCoordinator'

export interface ServiceWorkerUpdate {
  available: boolean
  apply(): Promise<void>
}

export function registerServiceWorker(onUpdate: (update: ServiceWorkerUpdate) => void): void {
  const updateServiceWorker = registerSW({
    immediate: true,
    onNeedRefresh() {
      onUpdate({
        available: true,
        async apply() {
          await waitForDraftWrites()
          await updateServiceWorker(true)
        },
      })
    },
  })
}
