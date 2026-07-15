<script setup lang="ts">
import { computed, ref } from 'vue'
import { receiveInventory } from '../api/inventory'
import QuantityInput from '../components/inventory/QuantityInput.vue'
import { useInventoryCommands } from '../inventory/useInventoryCommands'
import type { InventoryType, ReceiveInventoryInput } from '../inventory/inventoryModels'

const props = defineProps<{ role: string }>()
const emit = defineEmits<{ saved: [entryId: string] }>()
const intent = useInventoryCommands()
const type = ref<InventoryType>('BATCH')
const itemId = ref('')
const locationId = ref('')
const quantity = ref('1')
const batchNumber = ref('')
const assetNumber = ref('')
const serialNumber = ref('')
const productionDate = ref('')
const expirationDate = ref('')
const submitting = ref(false)
const error = ref('')
const canWrite = computed(() => props.role !== 'VIEWER')

function changed() {
  intent.changed()
}

async function submit() {
  if (!canWrite.value) return
  error.value = ''
  submitting.value = true
  const request: ReceiveInventoryInput = {
    itemId: itemId.value.trim(),
    locationId: locationId.value.trim(),
    type: type.value,
    quantity: quantity.value,
    receivedAt: new Date().toISOString(),
    productionDate: productionDate.value || null,
    expirationDate: expirationDate.value || null,
    batchNumber: type.value === 'BATCH' ? batchNumber.value.trim() || null : null,
    assetNumber: type.value === 'ASSET' ? assetNumber.value.trim() : null,
    serialNumber: type.value === 'ASSET' ? serialNumber.value.trim() || null : null,
    customAttributes: {},
  }
  try {
    const result = await intent.execute((key) => receiveInventory(request, key))
    emit('saved', result.id)
    intent.reset()
  } catch (problem) {
    const apiProblem = problem as { detail?: string; code?: string }
    error.value = apiProblem.detail ?? apiProblem.code ?? '入库失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="inventory-panel">
    <h2>新增入库</h2>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-form v-if="canWrite" class="inventory-form" label-position="top" @submit.prevent="submit">
      <el-form-item label="物品 ID"><el-input id="receive-item-id" v-model="itemId" required @input="changed" /></el-form-item>
      <el-form-item label="位置 ID"><el-input id="receive-location-id" v-model="locationId" required @input="changed" /></el-form-item>
      <el-form-item label="库存类型"><el-select id="receive-type" v-model="type" @change="changed"><el-option label="批次" value="BATCH" /><el-option label="资产" value="ASSET" /></el-select></el-form-item>
      <QuantityInput id="receive-quantity" v-model="quantity" label="数量" @update:model-value="changed" />
      <el-form-item v-if="type === 'BATCH'" label="批次号"><el-input id="batch-number" v-model="batchNumber" @input="changed" /></el-form-item>
      <template v-else>
        <el-form-item label="资产编号"><el-input id="asset-number" v-model="assetNumber" required @input="changed" /></el-form-item>
        <el-form-item label="序列号"><el-input id="serial-number" v-model="serialNumber" @input="changed" /></el-form-item>
      </template>
      <el-form-item label="生产日期"><el-input id="production-date" v-model="productionDate" type="date" @input="changed" /></el-form-item>
      <el-form-item label="到期日期"><el-input id="expiration-date" v-model="expirationDate" type="date" @input="changed" /></el-form-item>
      <el-button native-type="submit" type="primary" :loading="submitting">确认入库</el-button>
    </el-form>
    <el-alert v-else title="只读成员不能执行入库。" type="info" :closable="false" />
  </section>
</template>
