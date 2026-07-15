<script setup lang="ts">
import { onMounted, ref, toRaw } from 'vue'
import type { CurrentAccount } from '../auth/AuthState'
import { createItem, getItem, listCategories, searchCatalog } from '../api/catalog'
import { getInventoryAvailability, receiveInventory } from '../api/inventory'
import { listLocations, resolveLocationCode } from '../api/location'
import { IndexedDbDraftRepository } from '../offline/IndexedDbDraftRepository'
import type { DraftRepository } from '../offline/DraftRepository'
import type { ReceiveDraft } from '../receive/ReceiveDraft'
import type { ReceiveLocationSelection } from '../receive/ReceiveDraft'
import { createReceiveWizard, type ReceiveWizardController, type ReceiveWizardServices } from '../receive/useReceiveWizard'
import { useCatalogSearch } from '../catalog/useCatalogSearch'
import type { CatalogSearchItem, CategoryNode, ItemInput } from '../catalog/catalogModels'
import WizardProgress from '../components/receive/WizardProgress.vue'
import IdentifyStep from '../components/receive/IdentifyStep.vue'
import MatchStep from '../components/receive/MatchStep.vue'
import DetailsStep from '../components/receive/DetailsStep.vue'
import ConfirmStep from '../components/receive/ConfirmStep.vue'
import ScannerSheet from '../components/scanner/ScannerSheet.vue'
import type { Scanner, ScanResult } from '../scanner/Scanner'
import { ZxingScanner } from '../scanner/ZxingScanner'
import { BrowserEventScanner } from '../scanner/BrowserEventScanner'

const props = defineProps<{
  account?: CurrentAccount
  wizard?: ReceiveWizardController
  scanner?: Scanner
  draftRepository?: DraftRepository<ReceiveDraft>
  locations?: ReceiveLocationSelection[]
  categories?: CategoryNode[]
}>()

const categories = ref<CategoryNode[]>(props.categories ?? [])

function inventoryTypeFor(categoryId: string) {
  return categories.value.find(category => category.id === categoryId)?.defaultInventoryType ?? 'BATCH'
}

function productionServices(): ReceiveWizardServices {
  return {
    async findByBarcode(value) {
      const result = await searchCatalog(value)
      const match = result.items.find((candidate) => candidate.matchType === 'BARCODE_EXACT')
      if (!match) return undefined
      const found = await getItem(match.id)
      return { id: found.id, name: found.name, version: found.version, categoryId: found.categoryId, defaultInventoryType: inventoryTypeFor(found.categoryId) }
    },
    async resolveLocation(value) {
      const found = await resolveLocationCode(`stocket:location:${value}`)
      return { id: found.id, name: found.name, fullPath: found.fullPath, version: found.version }
    },
    getAvailability: getInventoryAvailability,
    async refreshItem(id) {
      const found = await getItem(id)
      return { id: found.id, name: found.name, version: found.version, categoryId: found.categoryId, defaultInventoryType: inventoryTypeFor(found.categoryId) }
    },
    async refreshLocation(id) {
      const found = (await listLocations()).find((candidate) => candidate.id === id)
      if (!found) throw new Error('LOCATION_NOT_FOUND')
      return { id: found.id, name: found.name, fullPath: found.fullPath, version: found.version }
    },
    receive: receiveInventory,
  }
}

const repository = props.draftRepository
  ? toRaw(props.draftRepository)
  : props.wizard
    ? undefined
    : new IndexedDbDraftRepository<ReceiveDraft>()
const wizard = props.wizard ?? createReceiveWizard(
  props.account?.id ?? 'anonymous',
  repository!,
  productionServices(),
)
const scanner = props.scanner ?? (import.meta.env.VITE_E2E_SCANNER === 'true'
  ? new BrowserEventScanner()
  : new ZxingScanner())
const scannerOpen = ref(false)
const locations = ref<ReceiveLocationSelection[]>(props.locations ?? [])
const locationLoadError = ref('')
const itemSelectionError = ref('')
const catalogSearch = useCatalogSearch()
const quickCreating = ref(false)
const createPending = ref(false)

async function handleScan(result: ScanResult) {
  await wizard.scan(result)
  scannerOpen.value = false
}

async function selectItem(item: CatalogSearchItem) {
  itemSelectionError.value = ''
  try {
    const found = await getItem(item.id)
    wizard.selectItem({
      id: found.id,
      name: found.name,
      version: found.version,
      categoryId: found.categoryId,
      defaultInventoryType: inventoryTypeFor(found.categoryId),
    })
  } catch (cause) {
    itemSelectionError.value = (cause as { detail?: string }).detail ?? '物品加载失败，请重新选择。'
  }
}

async function createNewItem(input: ItemInput) {
  itemSelectionError.value = ''
  createPending.value = true
  try {
    const created = await createItem(input)
    wizard.selectItem({
      id: created.id,
      name: created.name,
      version: created.version,
      categoryId: created.categoryId,
      defaultInventoryType: inventoryTypeFor(created.categoryId),
    })
    catalogSearch.query.value = ''
    quickCreating.value = false
  } catch (cause) {
    itemSelectionError.value = (cause as { detail?: string; code?: string }).detail
      ?? (cause as { code?: string }).code
      ?? '物品创建失败，请重试。'
  } finally {
    createPending.value = false
  }
}

onMounted(async () => {
  if (props.categories === undefined && !props.wizard) {
    try {
      categories.value = (await listCategories()).filter(category => !category.archived)
    } catch (cause) {
      itemSelectionError.value = (cause as { detail?: string }).detail ?? '分类加载失败，请稍后重试。'
    }
  }
  if (props.locations === undefined && !props.wizard) {
    try {
      locations.value = (await listLocations()).filter(location => !location.archived).map(location => ({
        id: location.id,
        name: location.name,
        fullPath: location.fullPath,
        version: location.version,
      }))
    } catch (cause) {
      locationLoadError.value = (cause as { detail?: string }).detail ?? '位置加载失败，请稍后重试。'
    }
  }
  if (!repository || wizard.state.value.kind !== 'IDENTIFY') return
  const latest = (await repository.list(props.account?.id ?? 'anonymous'))[0]
  if (latest) {
    await wizard.restore(latest.id)
    if (wizard.draft.value.location) locationLoadError.value = ''
    if (wizard.draft.value.item) itemSelectionError.value = ''
  }
})
</script>

<template>
  <section class="receive-wizard">
    <header class="receive-wizard__header">
      <p>快速入库</p>
      <h1>把物品放到正确的位置</h1>
      <span>跟随步骤完成识别、数量和位置确认，草稿会自动保存。</span>
    </header>
    <WizardProgress :current="wizard.state.value.kind" />
    <p v-if="locationLoadError" class="st-feedback st-feedback--error" role="alert">{{ locationLoadError }}</p>
    <p v-if="wizard.state.value.error" class="st-feedback st-feedback--error" role="alert">{{ wizard.state.value.error }}</p>
    <IdentifyStep v-if="wizard.state.value.kind === 'IDENTIFY'" @next="wizard.next" @scan="scannerOpen = true" />
    <MatchStep v-else-if="wizard.state.value.kind === 'MATCH'" v-model:query="catalogSearch.query.value" v-model:creating="quickCreating" :draft="wizard.draft.value" :results="catalogSearch.results.value" :loading="catalogSearch.loading.value" :error="itemSelectionError || catalogSearch.error.value" :categories="categories" :create-pending="createPending" :can-manage-categories="account?.role === 'ADMIN'" @select="selectItem" @create="createNewItem" @next="wizard.next" @back="wizard.back" />
    <DetailsStep v-else-if="wizard.state.value.kind === 'DETAILS'" :draft="wizard.draft.value" :locations="locations" :can-manage-locations="account?.role === 'ADMIN'" @update="wizard.updateDetails" @select-location="wizard.selectLocation" @next="wizard.next" @back="wizard.back" @scan-location="scannerOpen = true" />
    <ConfirmStep v-else-if="['CONFIRM', 'SUBMITTING', 'CONFLICT'].includes(wizard.state.value.kind)" :draft="wizard.draft.value" :current="wizard.preview.value.current" @submit="wizard.submit()" @back="wizard.back" />
    <section v-else-if="wizard.state.value.kind === 'COMPLETED'" class="receive-completed" role="status">
      <strong>入库完成</strong>
      <p>库存数量和流水已经更新。</p>
      <RouterLink class="st-button st-button--primary" :to="`/inventory/${wizard.state.value.entryId}`">查看入库物品</RouterLink>
    </section>
    <ScannerSheet v-model="scannerOpen" :scanner="scanner" @result="handleScan" />
  </section>
</template>
