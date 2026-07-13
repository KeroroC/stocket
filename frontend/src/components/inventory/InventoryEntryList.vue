<script setup lang="ts">
import type { InventoryEntry } from '../../inventory/inventoryModels'

defineProps<{ entries: InventoryEntry[]; selectedId?: string }>()
const emit = defineEmits<{ select: [entry: InventoryEntry] }>()
</script>

<template>
  <div class="inventory-list" aria-label="库存条目">
    <button
      v-for="entry in entries"
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
