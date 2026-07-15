<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getInventoryAvailability, getInventoryEntry, getInventoryMovements, listInventoryEntries } from '../api/inventory'
import AdjustSheet from '../components/inventory/AdjustSheet.vue'
import ConsumeSheet from '../components/inventory/ConsumeSheet.vue'
import InventoryEntryList from '../components/inventory/InventoryEntryList.vue'
import MovementTimeline from '../components/inventory/MovementTimeline.vue'
import TransferSheet from '../components/inventory/TransferSheet.vue'
import StEmptyState from '../components/StEmptyState.vue'
import StPageHeader from '../components/StPageHeader.vue'
import type { InventoryAvailability, InventoryEntry, InventoryMovement } from '../inventory/inventoryModels'
import InventoryReceiveView from './InventoryReceiveView.vue'
import DocumentList from '../components/attachment/DocumentList.vue'
import ExportDialog from '../components/export/ExportDialog.vue'

const props = defineProps<{ role: string; entryId?: string }>()
const entries = ref<InventoryEntry[]>([])
const selected = ref<InventoryEntry>()
const movements = ref<InventoryMovement[]>([])
const availability = ref<InventoryAvailability>()
const mode = ref<'list' | 'receive'>('list')
const sheet = ref<'consume' | 'transfer' | 'adjust'>()
const loading = ref(true)
const error = ref('')
const canWrite = computed(() => props.role !== 'VIEWER')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await listInventoryEntries({ page: 0, size: 50 })
    entries.value = [...page.items].sort((left, right) => {
      if (!left.expirationDate) return right.expirationDate ? 1 : 0
      if (!right.expirationDate) return -1
      return left.expirationDate.localeCompare(right.expirationDate)
    })
    let requested = props.entryId
      ? entries.value.find(entry => entry.id === props.entryId)
      : undefined
    if (props.entryId && !requested) {
      requested = await getInventoryEntry(props.entryId)
      entries.value.unshift(requested)
    }
    if (entries.value.length > 0) await select(requested ?? entries.value[0]!)
  } catch (problem) {
    error.value = (problem as { detail?: string; code?: string }).detail ?? '库存加载失败'
  } finally {
    loading.value = false
  }
}

async function select(entry: InventoryEntry) {
  selected.value = entry
  const [timeline, summary] = await Promise.all([
    getInventoryMovements(entry.id),
    getInventoryAvailability(entry.itemId),
  ])
  movements.value = timeline
  availability.value = summary
  mode.value = 'list'
}

async function saved(entryId: string) {
  await load()
  const created = entries.value.find((entry) => entry.id === entryId)
  if (created) await select(created)
}

async function completed() {
  sheet.value = undefined
  await load()
}
</script>

<template>
  <section class="st-page">
    <StPageHeader title="库存台账" description="查看批次、资产、可用量与不可变流水">
      <template #actions>
        <ExportDialog kind="inventory" label="导出库存" />
        <template v-if="canWrite">
          <button class="st-button st-button--primary" type="button" @click="mode = 'receive'">新增入库</button>
          <button v-if="selected" class="st-button" type="button" @click="sheet = 'consume'">消耗</button>
          <button v-if="selected" class="st-button" type="button" @click="sheet = 'transfer'">调拨</button>
          <button v-if="selected" class="st-button" type="button" @click="sheet = 'adjust'">调整</button>
        </template>
      </template>
    </StPageHeader>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="st-feedback">正在加载库存…</p>
    <InventoryReceiveView v-else-if="mode === 'receive'" :role="role" @saved="saved" />
    <StEmptyState v-else-if="entries.length === 0" title="暂无库存" description="完成首次入库后，库存条目会显示在这里。" />
    <div v-else class="inventory-workspace">
      <InventoryEntryList :entries="entries" :selected-id="selected?.id" @select="select" />
      <aside v-if="selected" class="inventory-detail">
        <h2>{{ selected.itemName }}</h2>
        <p v-if="selected.expirationDate">推荐：最早到期 {{ selected.expirationDate }}</p>
        <p>可用量：{{ availability?.totalAvailable ?? selected.quantity }}</p>
        <p v-if="availability?.earliestExpiration">最早到期：{{ availability.earliestExpiration }}</p>
        <MovementTimeline :movements="movements" />
        <DocumentList :key="selected.id" owner-type="INVENTORY_ENTRY" :owner-id="selected.id" :can-write="canWrite" />
      </aside>
    </div>
    <ConsumeSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'consume'" @close="sheet = undefined" @completed="completed" />
    <TransferSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'transfer'" @close="sheet = undefined" @completed="completed" />
    <AdjustSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'adjust'" @close="sheet = undefined" @completed="completed" />
  </section>
</template>
