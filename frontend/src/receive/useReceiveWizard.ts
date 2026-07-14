import { computed, ref, toRaw, type ComputedRef, type Ref } from 'vue'
import type { DraftRepository } from '../offline/DraftRepository'
import type { ReceiveInventoryInput } from '../inventory/inventoryModels'
import { trackDraftWrite } from '../pwa/updateCoordinator'
import type { ScanResult } from '../scanner/Scanner'
import { newReceiveDraft, type ReceiveDraft, type ReceiveItemSelection, type ReceiveLocationSelection } from './ReceiveDraft'
import type { ReceiveWizardState } from './ReceiveWizardState'

export interface ReceiveWizardServices {
  findByBarcode(value: string): Promise<ReceiveItemSelection | undefined>
  resolveLocation(value: string): Promise<ReceiveLocationSelection>
  getAvailability(itemId: string): Promise<{ totalAvailable: string }>
  refreshItem(itemId: string): Promise<ReceiveItemSelection>
  refreshLocation(locationId: string): Promise<ReceiveLocationSelection>
  receive(input: ReceiveInventoryInput, idempotencyKey: string): Promise<{ id: string }>
}

export interface ReceiveWizardController {
  state: Ref<ReceiveWizardState>
  draft: Ref<ReceiveDraft>
  preview: ComputedRef<{ current: string; change: string }>
  scan(result: ScanResult): Promise<void>
  selectItem(item: ReceiveItemSelection): void
  selectLocation(location: ReceiveLocationSelection): void
  updateDetails(values: Partial<ReceiveDraft>): void
  next(): void
  back(): void
  goToConfirm(): void
  restore(draftId: string): Promise<boolean>
  flush(): Promise<void>
  submit(options?: { acceptVersions?: boolean }): Promise<void>
}

export function createReceiveWizard(
  accountId: string,
  repository: DraftRepository<any>,
  services: ReceiveWizardServices,
): ReceiveWizardController {
  const state = ref<ReceiveWizardState>({ kind: 'IDENTIFY' })
  const draft = ref<ReceiveDraft>(newReceiveDraft())
  const availability = ref('0')
  let saveTimer: ReturnType<typeof setTimeout> | undefined

  const preview = computed(() => ({ current: availability.value, change: draft.value.quantity }))

  function touch(regenerateIntent = true) {
    draft.value.updatedAt = new Date().toISOString()
    if (regenerateIntent) draft.value.idempotencyKey = crypto.randomUUID()
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => void flush(), 300)
  }

  async function scan(result: ScanResult) {
    draft.value.lastScan = result
    if (result.kind === 'PRODUCT_BARCODE') {
      const match = await services.findByBarcode(result.value)
      if (match) selectItem(match)
      state.value = { kind: 'MATCH' }
    } else {
      selectLocation(await services.resolveLocation(result.value))
      if (state.value.kind === 'IDENTIFY') state.value = { kind: 'DETAILS' }
    }
    touch(false)
  }

  function selectItem(item: ReceiveItemSelection) {
    draft.value.item = { ...item }
    draft.value.inventoryType = item.defaultInventoryType
    touch()
  }

  function selectLocation(location: ReceiveLocationSelection) {
    draft.value.location = { ...location }
    touch()
  }

  function updateDetails(values: Partial<ReceiveDraft>) {
    Object.assign(draft.value, values)
    touch()
  }

  function next() {
    const nextKind = state.value.kind === 'IDENTIFY' ? 'MATCH'
      : state.value.kind === 'MATCH' ? 'DETAILS'
        : state.value.kind === 'DETAILS' ? 'CONFIRM' : state.value.kind
    state.value = { kind: nextKind }
    if (nextKind === 'CONFIRM' && draft.value.item) {
      void services.getAvailability(draft.value.item.id).then((value) => {
        availability.value = value.totalAvailable
      }).catch(() => undefined)
    }
  }

  function back() {
    const previous = state.value.kind === 'CONFIRM' || state.value.kind === 'CONFLICT' ? 'DETAILS'
      : state.value.kind === 'DETAILS' ? 'MATCH'
        : state.value.kind === 'MATCH' ? 'IDENTIFY' : state.value.kind
    state.value = { kind: previous }
  }

  function goToConfirm() {
    state.value = { kind: 'CONFIRM' }
    if (draft.value.item) {
      void services.getAvailability(draft.value.item.id).then((value) => {
        availability.value = value.totalAvailable
      }).catch(() => undefined)
    }
  }

  async function restore(draftId: string) {
    const restored = await repository.get(accountId, draftId) as ReceiveDraft | undefined
    if (!restored) return false
    draft.value = restored
    state.value = { kind: restored.item ? 'DETAILS' : 'IDENTIFY' }
    return true
  }

  async function flush() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = undefined
    const snapshot = structuredClone(toRaw(draft.value))
    await trackDraftWrite(repository.save(accountId, snapshot))
  }

  async function submit(options: { acceptVersions?: boolean } = {}) {
    const selectedItem = draft.value.item
    const selectedLocation = draft.value.location
    if (!selectedItem || !selectedLocation) {
      state.value = { kind: 'CONFIRM', error: '请选择物品和位置。' }
      return
    }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      state.value = { kind: 'CONFIRM', error: '当前离线，请联网后再提交；草稿已保留。' }
      await flush()
      return
    }
    state.value = { kind: 'SUBMITTING' }
    try {
      const [currentItem, currentLocation] = await Promise.all([
        services.refreshItem(selectedItem.id),
        services.refreshLocation(selectedLocation.id),
      ])
      const conflicts: string[] = []
      if (currentItem.version !== selectedItem.version) conflicts.push('物品定义已更新')
      if (currentLocation.version !== selectedLocation.version) conflicts.push('位置已更新')
      if (conflicts.length && !options.acceptVersions) {
        state.value = { kind: 'CONFLICT', conflicts }
        await flush()
        return
      }
      draft.value.item = currentItem
      draft.value.location = currentLocation
      const input: ReceiveInventoryInput = {
        itemId: currentItem.id,
        locationId: currentLocation.id,
        type: draft.value.inventoryType,
        quantity: draft.value.quantity,
        receivedAt: new Date().toISOString(),
        productionDate: draft.value.productionDate || null,
        expirationDate: draft.value.expirationDate || null,
        batchNumber: draft.value.inventoryType === 'BATCH' ? draft.value.batchNumber || null : null,
        assetNumber: draft.value.inventoryType === 'ASSET' ? draft.value.assetNumber : null,
        serialNumber: draft.value.inventoryType === 'ASSET' ? draft.value.serialNumber || null : null,
        customAttributes: {},
      }
      const result = await services.receive(input, draft.value.idempotencyKey)
      await repository.delete(accountId, draft.value.id)
      state.value = { kind: 'COMPLETED', entryId: result.id }
    } catch (error) {
      const code = (error as { code?: string }).code
      state.value = {
        kind: 'CONFIRM',
        error: code === 'OFFLINE_WRITE_BLOCKED'
          ? '当前离线，请联网后再提交；草稿已保留。'
          : '入库失败，草稿已保留。',
      }
      await flush()
    }
  }

  return { state, draft, preview, scan, selectItem, selectLocation, updateDetails, next, back, goToConfirm, restore, flush, submit }
}
