<script setup lang="ts">
import type { InventoryEntry } from '../../inventory/inventoryModels'
import { useDesktopLayout } from '../../composables/useDesktopLayout'

const { isDesktop } = useDesktopLayout()

defineProps<{ entries: InventoryEntry[]; selectedId?: string }>()
const emit = defineEmits<{ select: [entry: InventoryEntry] }>()
</script>

<template>
  <div class="inventory-list" aria-label="库存条目">
    <el-table v-if="isDesktop && entries.length" :data="entries" row-key="id" highlight-current-row :current-row-key="selectedId" class="inventory-table" @row-click="emit('select', $event)">
      <el-table-column prop="itemName" label="物品" min-width="160" />
      <el-table-column prop="locationName" label="位置" min-width="140" />
      <el-table-column label="类型" width="90"><template #default="{ row }"><el-tag effect="plain">{{ row.type === 'BATCH' ? '批次' : '资产' }}</el-tag></template></el-table-column>
      <el-table-column prop="quantity" label="数量" width="100" align="right" />
      <el-table-column label="到期日" min-width="130"><template #default="{ row }">{{ row.expirationDate ?? '无到期日' }}</template></el-table-column>
    </el-table>
    <el-card
      v-for="entry in entries"
      v-show="!isDesktop"
      :key="entry.id"
      :class="['inventory-card', { selected: selectedId === entry.id }]"
      @click="emit('select', entry)"
      shadow="hover"
      tabindex="0"
      role="button"
      :aria-pressed="selectedId === entry.id"
      :aria-label="`${entry.itemName}，${entry.locationName}，数量 ${entry.quantity}`"
      @keydown.enter="emit('select', entry)"
      @keydown.space.prevent="emit('select', entry)"
    >
      <div class="inventory-card__heading">
        <span class="inventory-card-title">{{ entry.itemName }}</span>
        <el-tag size="small" effect="plain">{{ entry.type === 'BATCH' ? '批次' : '资产' }}</el-tag>
      </div>
      <span class="inventory-card__location">{{ entry.locationName }}</span>
      <div class="inventory-card__footer">
        <strong>{{ entry.quantity }}</strong>
        <span v-if="entry.expirationDate">到期 {{ entry.expirationDate }}</span>
        <span v-else>无到期日</span>
      </div>
    </el-card>
  </div>
</template>
