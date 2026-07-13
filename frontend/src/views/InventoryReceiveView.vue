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
    <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
    <form v-if="canWrite" class="inventory-form" @submit.prevent="submit">
      <label class="inventory-field" for="receive-item-id"><span>物品 ID</span><input id="receive-item-id" v-model="itemId" required @input="changed" /></label>
      <label class="inventory-field" for="receive-location-id"><span>位置 ID</span><input id="receive-location-id" v-model="locationId" required @input="changed" /></label>
      <label class="inventory-field" for="receive-type"><span>库存类型</span><select id="receive-type" v-model="type" @change="changed"><option value="BATCH">批次</option><option value="ASSET">资产</option></select></label>
      <QuantityInput id="receive-quantity" v-model="quantity" label="数量" @update:model-value="changed" />
      <label v-if="type === 'BATCH'" class="inventory-field" for="batch-number"><span>批次号</span><input id="batch-number" v-model="batchNumber" @input="changed" /></label>
      <template v-else>
        <label class="inventory-field" for="asset-number"><span>资产编号</span><input id="asset-number" v-model="assetNumber" required @input="changed" /></label>
        <label class="inventory-field" for="serial-number"><span>序列号</span><input id="serial-number" v-model="serialNumber" @input="changed" /></label>
      </template>
      <label class="inventory-field" for="production-date"><span>生产日期</span><input id="production-date" v-model="productionDate" type="date" @input="changed" /></label>
      <label class="inventory-field" for="expiration-date"><span>到期日期</span><input id="expiration-date" v-model="expirationDate" type="date" @input="changed" /></label>
      <button type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '确认入库' }}</button>
    </form>
    <p v-else>只读成员不能执行入库。</p>
  </section>
</template>
