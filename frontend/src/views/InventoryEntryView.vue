<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getInventoryAvailability, getInventoryMovements, listInventoryEntries } from '../api/inventory'
import AdjustSheet from '../components/inventory/AdjustSheet.vue'
import ConsumeSheet from '../components/inventory/ConsumeSheet.vue'
import InventoryEntryList from '../components/inventory/InventoryEntryList.vue'
import MovementTimeline from '../components/inventory/MovementTimeline.vue'
import TransferSheet from '../components/inventory/TransferSheet.vue'
import StEmptyState from '../components/StEmptyState.vue'
import StPageHeader from '../components/StPageHeader.vue'
import type { InventoryAvailability, InventoryEntry, InventoryMovement } from '../inventory/inventoryModels'
import InventoryReceiveView from './InventoryReceiveView.vue'

const props = defineProps<{ role: string }>()
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
    if (entries.value.length > 0) await select(entries.value[0]!)
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
  <section>
    <StPageHeader title="库存台账" description="查看批次、资产、可用量与不可变流水">
      <template v-if="canWrite" #actions>
        <button type="button" @click="mode = 'receive'">新增入库</button>
        <button v-if="selected" type="button" @click="sheet = 'consume'">消耗</button>
        <button v-if="selected" type="button" @click="sheet = 'transfer'">调拨</button>
        <button v-if="selected" type="button" @click="sheet = 'adjust'">调整</button>
      </template>
    </StPageHeader>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="loading">正在加载库存…</p>
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
      </aside>
    </div>
    <ConsumeSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'consume'" @close="sheet = undefined" @completed="completed" />
    <TransferSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'transfer'" @close="sheet = undefined" @completed="completed" />
    <AdjustSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'adjust'" @close="sheet = undefined" @completed="completed" />
  </section>
</template>
