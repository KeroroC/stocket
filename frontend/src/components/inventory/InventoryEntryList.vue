<script setup lang="ts">
import type { InventoryEntry } from '../../inventory/inventoryModels'
import { useDesktopLayout } from '../../composables/useDesktopLayout'

const { isDesktop } = useDesktopLayout()

defineProps<{ entries: InventoryEntry[]; selectedId?: string }>()
const emit = defineEmits<{ select: [entry: InventoryEntry] }>()
</script>

<template>
  <div class="inventory-list" aria-label="库存条目">
    <div v-if="isDesktop" class="st-table-wrapper inventory-table">
      <table class="st-table">
        <thead>
          <tr>
            <th>物品</th>
            <th>位置</th>
            <th>类型</th>
            <th>数量</th>
            <th>到期日</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="entry in entries"
            :key="`table-${entry.id}`"
            :class="{ selected: selectedId === entry.id }"
            tabindex="0"
            @click="emit('select', entry)"
            @keydown.enter="emit('select', entry)"
          >
            <td>{{ entry.itemName }}</td>
            <td>{{ entry.locationName }}</td>
            <td>{{ entry.type === 'BATCH' ? '批次' : '资产' }}</td>
            <td>{{ entry.quantity }}</td>
            <td>{{ entry.expirationDate ?? '无到期日' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <button
      v-for="entry in entries"
      v-show="!isDesktop"
      :key="entry.id"
      type="button"
      :class="['inventory-card', { selected: selectedId === entry.id }]"
      @click="emit('select', entry)"
    >
      <span class="inventory-card-title">{{ entry.itemName }}</span>
      <span>{{ entry.locationName }} · {{ entry.type === 'BATCH' ? '批次' : '资产' }}</span>
      <strong>{{ entry.quantity }}</strong>
      <span v-if="entry.expirationDate">到期 {{ entry.expirationDate }}</span>
      <span v-else>无到期日</span>
    </button>
  </div>
</template>
