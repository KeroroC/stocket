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
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />
    <el-form v-if="canWrite" class="inventory-form" label-position="top" @submit.prevent="submit">
      <el-form-item label="操作"><el-select id="inventory-operation" v-model="operation" @change="changed"><el-option label="消耗" value="consume" /><el-option label="退库" value="return" /><el-option label="调整" value="adjust" /><el-option label="调拨" value="transfer" /><el-option label="标记丢失" value="lost" /><el-option label="报废" value="retire" /></el-select></el-form-item>
      <QuantityInput v-if="!['lost', 'retire'].includes(operation)" id="operate-quantity" v-model="quantity" label="操作数量" @update:model-value="changed" />
      <el-form-item v-if="operation === 'transfer'" label="目标位置 ID"><el-input id="target-location" v-model="targetLocationId" required @input="changed" /></el-form-item>
      <el-form-item v-if="['return', 'adjust', 'lost', 'retire'].includes(operation)" label="原因"><el-input id="operation-reason" v-model="reason" @input="changed" /></el-form-item>
      <el-button native-type="submit" type="primary" :loading="submitting">{{ error ? '重试' : '确认操作' }}</el-button>
    </el-form>
    <el-alert v-else title="只读成员不能执行库存操作。" type="info" :closable="false" />
  </section>
</template>
