<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  adjustInventory,
  consumeInventory,
  markInventoryLost,
  retireInventory,
  returnInventory,
  transferInventory,
} from '../api/inventory'
import QuantityInput from '../components/inventory/QuantityInput.vue'
import { useInventoryCommands } from '../inventory/useInventoryCommands'

const props = defineProps<{ role: string; entryId: string }>()
const emit = defineEmits<{ completed: [] }>()
const operation = ref('consume')
const quantity = ref('1')
const targetLocationId = ref('')
const reason = ref('')
const error = ref('')
const success = ref('')
const submitting = ref(false)
const intent = useInventoryCommands()
const canWrite = computed(() => props.role !== 'VIEWER')

function changed() {
  intent.changed()
  error.value = ''
  success.value = ''
}

async function submit() {
  if (!canWrite.value) return
  error.value = ''
  success.value = ''
  submitting.value = true
  try {
    await intent.execute((key) => {
      if (operation.value === 'return') return returnInventory(props.entryId, { quantity: quantity.value, reason: reason.value || undefined }, key)
      if (operation.value === 'adjust') return adjustInventory(props.entryId, { targetQuantity: quantity.value, reason: reason.value }, key)
      if (operation.value === 'transfer') return transferInventory(props.entryId, { targetLocationId: targetLocationId.value, quantity: quantity.value }, key)
      if (operation.value === 'lost') return markInventoryLost(props.entryId, { reason: reason.value }, key)
      if (operation.value === 'retire') return retireInventory(props.entryId, { reason: reason.value }, key)
      return consumeInventory(props.entryId, { quantity: quantity.value }, key)
    })
    success.value = '操作完成'
    emit('completed')
  } catch (problem) {
    const apiProblem = problem as { detail?: string; code?: string }
    error.value = apiProblem.detail ?? apiProblem.code ?? '操作失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="inventory-panel">
    <h2>库存操作</h2>
    <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
    <p v-if="success" role="status">{{ success }}</p>
    <form v-if="canWrite" class="inventory-form" @submit.prevent="submit">
      <label class="inventory-field" for="inventory-operation"><span>操作</span><select id="inventory-operation" v-model="operation" @change="changed"><option value="consume">消耗</option><option value="return">退库</option><option value="adjust">调整</option><option value="transfer">调拨</option><option value="lost">标记丢失</option><option value="retire">报废</option></select></label>
      <QuantityInput v-if="!['lost', 'retire'].includes(operation)" id="operate-quantity" v-model="quantity" label="操作数量" @update:model-value="changed" />
      <label v-if="operation === 'transfer'" class="inventory-field" for="target-location"><span>目标位置 ID</span><input id="target-location" v-model="targetLocationId" required @input="changed" /></label>
      <label v-if="['return', 'adjust', 'lost', 'retire'].includes(operation)" class="inventory-field" for="operation-reason"><span>原因</span><input id="operation-reason" v-model="reason" @input="changed" /></label>
      <button type="submit" :disabled="submitting">{{ error ? '重试' : '确认操作' }}</button>
    </form>
    <p v-else>只读成员不能执行库存操作。</p>
  </section>
</template>
