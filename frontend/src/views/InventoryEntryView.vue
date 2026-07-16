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
const query = ref('')
const typeFilter = ref<'ALL' | 'BATCH' | 'ASSET'>('ALL')
const canWrite = computed(() => props.role !== 'VIEWER')
const filteredEntries = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase()
  return entries.value.filter((entry) => {
    const matchesType = typeFilter.value === 'ALL' || entry.type === typeFilter.value
    const matchesQuery = !keyword
      || entry.itemName.toLocaleLowerCase().includes(keyword)
      || entry.locationName.toLocaleLowerCase().includes(keyword)
    return matchesType && matchesQuery
  })
})
const batchCount = computed(() => entries.value.filter(entry => entry.type === 'BATCH').length)
const assetCount = computed(() => entries.value.filter(entry => entry.type === 'ASSET').length)
const nearestExpiration = computed(() => entries.value.find(entry => entry.expirationDate)?.expirationDate)

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
  <section class="st-page inventory-page">
    <StPageHeader title="库存台账" description="集中查看库存条目、可用量、到期日期与每一次变动">
      <template #actions>
        <ExportDialog v-if="mode === 'list'" kind="inventory" label="导出库存" />
        <el-button v-if="canWrite && mode === 'list'" type="primary" @click="mode = 'receive'">新增入库</el-button>
        <el-button v-else-if="mode === 'receive'" @click="mode = 'list'">返回台账</el-button>
      </template>
    </StPageHeader>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <el-skeleton v-else-if="loading" :rows="6" animated />
    <InventoryReceiveView v-else-if="mode === 'receive'" :role="role" @saved="saved" />
    <StEmptyState v-else-if="entries.length === 0" title="暂无库存" description="完成首次入库后，库存条目会显示在这里。" />
    <template v-else>
      <dl class="inventory-overview" aria-label="库存概览">
        <div>
          <dt>库存条目</dt>
          <dd>{{ entries.length }}</dd>
          <span>当前有效记录</span>
        </div>
        <div>
          <dt>批次 / 资产</dt>
          <dd>{{ batchCount }} / {{ assetCount }}</dd>
          <span>两类库存构成</span>
        </div>
        <div>
          <dt>最近到期</dt>
          <dd class="inventory-overview__date">{{ nearestExpiration ?? '暂无' }}</dd>
          <span>{{ nearestExpiration ? '已按日期优先排列' : '没有设置到期日' }}</span>
        </div>
      </dl>
      <div class="inventory-workspace">
        <section class="inventory-browser" aria-labelledby="inventory-list-title">
          <header class="inventory-browser__header">
            <div>
              <p>库存浏览</p>
              <h2 id="inventory-list-title">库存条目</h2>
            </div>
            <span aria-live="polite">{{ filteredEntries.length }} 项</span>
          </header>
          <div class="inventory-browser__filters">
            <el-input v-model="query" type="search" aria-label="筛选库存条目" placeholder="搜索物品或位置" clearable />
            <el-segmented
              v-model="typeFilter"
              :options="[
                { label: '全部', value: 'ALL' },
                { label: '批次', value: 'BATCH' },
                { label: '资产', value: 'ASSET' },
              ]"
              aria-label="库存类型"
            />
          </div>
          <InventoryEntryList :entries="filteredEntries" :selected-id="selected?.id" @select="select" />
          <StEmptyState v-if="filteredEntries.length === 0" title="没有匹配的库存" description="尝试清空关键词或切换库存类型。" />
        </section>
        <el-card v-if="selected" class="inventory-detail" shadow="never">
          <header class="inventory-detail__header">
            <div>
              <p>{{ selected.locationName }} · {{ selected.type === 'BATCH' ? '批次库存' : '资产库存' }}</p>
              <h2>{{ selected.itemName }}</h2>
              <span v-if="selected.expirationDate">推荐：最早到期 {{ selected.expirationDate }}</span>
              <span v-else>当前条目没有设置到期日</span>
            </div>
            <el-tag effect="plain">{{ selected.type === 'BATCH' ? '批次' : '资产' }}</el-tag>
          </header>
          <dl class="inventory-detail__summary" aria-label="当前库存摘要">
            <div>
              <dt>条目数量</dt>
              <dd>{{ selected.quantity }}</dd>
            </div>
            <div>
              <dt>物品可用量</dt>
              <dd><span class="sr-only">可用量：</span>{{ availability?.totalAvailable ?? selected.quantity }}</dd>
            </div>
            <div>
              <dt>最早到期</dt>
              <dd>{{ availability?.earliestExpiration ?? '暂无' }}</dd>
            </div>
          </dl>
          <div v-if="canWrite" class="inventory-detail__actions" role="group" aria-label="当前库存操作">
            <el-button @click="sheet = 'consume'">消耗</el-button>
            <el-button @click="sheet = 'transfer'">调拨</el-button>
            <el-button @click="sheet = 'adjust'">调整</el-button>
          </div>
          <section class="inventory-detail__section" aria-labelledby="movement-title">
            <header>
              <div>
                <p>变动记录</p>
                <h3 id="movement-title">库存流水</h3>
              </div>
              <span>{{ movements.length }} 条</span>
            </header>
            <MovementTimeline :movements="movements" />
            <StEmptyState v-if="movements.length === 0" title="暂无库存流水" description="入库或调整后，变动记录会显示在这里。" />
          </section>
          <DocumentList :key="selected.id" owner-type="INVENTORY_ENTRY" :owner-id="selected.id" :can-write="canWrite" />
        </el-card>
      </div>
    </template>
    <ConsumeSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'consume'" @close="sheet = undefined" @completed="completed" />
    <TransferSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'transfer'" @close="sheet = undefined" @completed="completed" />
    <AdjustSheet v-if="selected" :entry-id="selected.id" :open="sheet === 'adjust'" @close="sheet = undefined" @completed="completed" />
  </section>
</template>
