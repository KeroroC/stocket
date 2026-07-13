<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getInventoryAvailability, getInventoryMovements, listInventoryEntries } from '../api/inventory'
import InventoryEntryList from '../components/inventory/InventoryEntryList.vue'
import MovementTimeline from '../components/inventory/MovementTimeline.vue'
import StEmptyState from '../components/StEmptyState.vue'
import StPageHeader from '../components/StPageHeader.vue'
import type { InventoryAvailability, InventoryEntry, InventoryMovement } from '../inventory/inventoryModels'
import InventoryOperateView from './InventoryOperateView.vue'
import InventoryReceiveView from './InventoryReceiveView.vue'

const props = defineProps<{ role: string }>()
const entries = ref<InventoryEntry[]>([])
const selected = ref<InventoryEntry>()
const movements = ref<InventoryMovement[]>([])
const availability = ref<InventoryAvailability>()
const mode = ref<'list' | 'receive' | 'operate'>('list')
const loading = ref(true)
const error = ref('')
const canWrite = computed(() => props.role !== 'VIEWER')

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const page = await listInventoryEntries({ page: 0, size: 50 })
    entries.value = page.items
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
</script>

<template>
  <section>
    <StPageHeader title="库存台账" description="查看批次、资产、可用量与不可变流水">
      <template v-if="canWrite" #actions>
        <button type="button" @click="mode = 'receive'">新增入库</button>
        <button v-if="selected" type="button" @click="mode = 'operate'">库存操作</button>
      </template>
    </StPageHeader>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="loading">正在加载库存…</p>
    <InventoryReceiveView v-else-if="mode === 'receive'" :role="role" @saved="saved" />
    <InventoryOperateView v-else-if="mode === 'operate' && selected" :role="role" :entry-id="selected.id" @completed="select(selected)" />
    <StEmptyState v-else-if="entries.length === 0" title="暂无库存" description="完成首次入库后，库存条目会显示在这里。" />
    <div v-else class="inventory-workspace">
      <InventoryEntryList :entries="entries" :selected-id="selected?.id" @select="select" />
      <aside v-if="selected" class="inventory-detail">
        <h2>{{ selected.itemName }}</h2>
        <p>可用量：{{ availability?.totalAvailable ?? selected.quantity }}</p>
        <p v-if="availability?.earliestExpiration">最早到期：{{ availability.earliestExpiration }}</p>
        <MovementTimeline :movements="movements" />
      </aside>
    </div>
  </section>
</template>
