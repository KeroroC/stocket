<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Camera, Check } from '@element-plus/icons-vue'
import { transferInventory } from '../../api/inventory'
import { listLocations, resolveLocationCode, type LocationNode } from '../../api/location'
import { useInventoryCommands } from '../../inventory/useInventoryCommands'
import type { Scanner, ScanResult } from '../../scanner/Scanner'
import { ZxingScanner } from '../../scanner/ZxingScanner'
import ScannerSheet from '../scanner/ScannerSheet.vue'
import QuantityInput from './QuantityInput.vue'

const props = withDefaults(defineProps<{ entryId: string; open: boolean; scanner?: Scanner }>(), {
  scanner: () => new ZxingScanner(),
})
const emit = defineEmits<{ close: []; completed: [] }>()
const locations = ref<LocationNode[]>([])
const targetLocationId = ref('')
const quantity = ref('1')
const scanning = ref(false)
const error = ref('')
const submitting = ref(false)
const intent = useInventoryCommands()

onMounted(async () => {
  try {
    locations.value = (await listLocations()).filter(location => !location.archived)
  } catch (cause) {
    error.value = (cause as { detail?: string }).detail ?? '位置加载失败'
  }
})

function changed() {
  intent.changed()
  error.value = ''
}

async function scanned(result: ScanResult) {
  if (result.kind !== 'LOCATION_CODE') {
    error.value = '请扫描位置码'
    return
  }
  try {
    const location = locations.value.find(candidate => candidate.publicCode.toUpperCase() === result.value)
      ?? await resolveLocationCode(`stocket:location:${result.value}`)
    if (!locations.value.some(candidate => candidate.id === location.id)) locations.value.push(location)
    targetLocationId.value = location.id
    changed()
    scanning.value = false
  } catch (cause) {
    error.value = (cause as { detail?: string }).detail ?? '无法识别目标位置'
  }
}

async function submit() {
  if (!targetLocationId.value) {
    error.value = '请选择目标位置'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    await intent.execute(key => transferInventory(props.entryId, {
      targetLocationId: targetLocationId.value,
      quantity: quantity.value,
    }, key))
    intent.reset()
    emit('completed')
  } catch (cause) {
    const problem = cause as { detail?: string; code?: string; status?: number }
    error.value = problem.detail ?? problem.code ?? (problem.status === 403 ? '无权限执行库存操作' : '调拨库存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="open" title="调拨库存" width="min(34rem, calc(100vw - 2rem))" :teleported="false" :destroy-on-close="false" @close="emit('close')">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-form label-position="top" @submit.prevent="submit">
      <QuantityInput id="transfer-quantity" v-model="quantity" label="调拨数量" @update:model-value="changed" />
      <el-form-item label="目标位置"><el-select id="target-location" v-model="targetLocationId" filterable @change="changed"><el-option v-for="location in locations" :key="location.id" :label="location.fullPath" :value="location.id" /></el-select></el-form-item>
      <el-button :icon="Camera" @click="scanning = true">扫描目标位置</el-button>
    </el-form>
    <template #footer><el-button @click="emit('close')">取消</el-button><el-button type="primary" :icon="Check" :loading="submitting" @click="submit">{{ error ? '重试调拨' : '确认调拨' }}</el-button></template>
    <ScannerSheet v-model="scanning" :scanner="scanner" @result="scanned" />
  </el-dialog>
</template>
