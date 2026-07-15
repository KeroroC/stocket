<script setup lang="ts">
import { ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { consumeInventory } from '../../api/inventory'
import { useInventoryCommands } from '../../inventory/useInventoryCommands'
import QuantityInput from './QuantityInput.vue'

const props = defineProps<{ entryId: string; open: boolean }>()
const emit = defineEmits<{ close: []; completed: [] }>()
const quantity = ref('1')
const error = ref('')
const submitting = ref(false)
const intent = useInventoryCommands()

function changed() {
  intent.changed()
  error.value = ''
}

async function submit() {
  error.value = ''
  submitting.value = true
  try {
    await intent.execute(key => consumeInventory(props.entryId, { quantity: quantity.value }, key))
    intent.reset()
    emit('completed')
  } catch (cause) {
    const problem = cause as { detail?: string; code?: string; status?: number }
    error.value = problem.detail ?? problem.code ?? (problem.status === 403 ? '无权限执行库存操作' : '消耗库存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="open" title="消耗库存" width="min(34rem, calc(100vw - 2rem))" :teleported="false" :destroy-on-close="false" @close="emit('close')">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-form label-position="top" @submit.prevent="submit">
      <QuantityInput id="consume-quantity" v-model="quantity" label="消耗数量" @update:model-value="changed" />
    </el-form>
    <template #footer><el-button @click="emit('close')">取消</el-button><el-button type="primary" :icon="Check" :loading="submitting" @click="submit">{{ error ? '重试消耗' : '确认消耗' }}</el-button></template>
  </el-dialog>
</template>
