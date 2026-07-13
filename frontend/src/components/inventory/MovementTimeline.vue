<script setup lang="ts">
import type { InventoryMovement } from '../../inventory/inventoryModels'

defineProps<{ movements: InventoryMovement[] }>()
</script>

<template>
  <ol class="movement-timeline" aria-label="库存流水">
    <li v-for="movement in movements" :key="movement.id">
      <div class="movement-heading">
        <strong>{{ movement.type }}</strong>
        <span>{{ movement.quantityDelta }}</span>
      </div>
      <p v-if="movement.reason">原因：{{ movement.reason }}</p>
      <p>操作人：{{ movement.actorDisplayName }}</p>
      <time :datetime="movement.occurredAt">{{ new Date(movement.occurredAt).toLocaleString() }}</time>
    </li>
  </ol>
</template>
