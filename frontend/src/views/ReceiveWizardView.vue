<script setup lang="ts">
import type { CurrentAccount } from '../auth/AuthState'
import { getItem, searchCatalog } from '../api/catalog'
import { getInventoryAvailability, receiveInventory } from '../api/inventory'
import { listLocations, resolveLocationCode } from '../api/location'
import { IndexedDbDraftRepository } from '../offline/IndexedDbDraftRepository'
import type { ReceiveDraft } from '../receive/ReceiveDraft'
import { createReceiveWizard, type ReceiveWizardController, type ReceiveWizardServices } from '../receive/useReceiveWizard'
import WizardProgress from '../components/receive/WizardProgress.vue'
import IdentifyStep from '../components/receive/IdentifyStep.vue'
import MatchStep from '../components/receive/MatchStep.vue'
import DetailsStep from '../components/receive/DetailsStep.vue'
import ConfirmStep from '../components/receive/ConfirmStep.vue'

const props = defineProps<{ account?: CurrentAccount; wizard?: ReceiveWizardController }>()

function productionServices(): ReceiveWizardServices {
  return {
    async findByBarcode(value) {
      const result = await searchCatalog(value)
      const match = result.items.find((candidate) => candidate.matchType === 'BARCODE_EXACT')
      if (!match) return undefined
      const found = await getItem(match.id)
      return { id: found.id, name: found.name, version: found.version, categoryId: found.categoryId, defaultInventoryType: 'BATCH' }
    },
    async resolveLocation(value) {
      const found = await resolveLocationCode(`stocket:location:${value}`)
      return { id: found.id, name: found.name, version: found.version }
    },
    getAvailability: getInventoryAvailability,
    async refreshItem(id) {
      const found = await getItem(id)
      return { id: found.id, name: found.name, version: found.version, categoryId: found.categoryId, defaultInventoryType: 'BATCH' }
    },
    async refreshLocation(id) {
      const found = (await listLocations()).find((candidate) => candidate.id === id)
      if (!found) throw new Error('LOCATION_NOT_FOUND')
      return { id: found.id, name: found.name, version: found.version }
    },
    receive: receiveInventory,
  }
}

const wizard = props.wizard ?? createReceiveWizard(
  props.account?.id ?? 'anonymous',
  new IndexedDbDraftRepository<ReceiveDraft>(),
  productionServices(),
)
</script>

<template>
  <section class="receive-wizard">
    <WizardProgress :current="wizard.state.value.kind" />
    <p v-if="wizard.state.value.error" role="alert">{{ wizard.state.value.error }}</p>
    <IdentifyStep v-if="wizard.state.value.kind === 'IDENTIFY'" @next="wizard.next" />
    <MatchStep v-else-if="wizard.state.value.kind === 'MATCH'" :draft="wizard.draft.value" @next="wizard.next" @back="wizard.back" />
    <DetailsStep v-else-if="wizard.state.value.kind === 'DETAILS'" :draft="wizard.draft.value" @update="wizard.updateDetails" @next="wizard.next" @back="wizard.back" />
    <ConfirmStep v-else-if="['CONFIRM', 'SUBMITTING', 'CONFLICT'].includes(wizard.state.value.kind)" :draft="wizard.draft.value" :current="wizard.preview.value.current" @submit="wizard.submit()" @back="wizard.back" />
    <p v-else-if="wizard.state.value.kind === 'COMPLETED'">入库完成</p>
  </section>
</template>
