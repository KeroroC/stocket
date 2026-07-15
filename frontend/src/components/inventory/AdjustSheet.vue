<script setup lang="ts">
import { ref } from 'vue'
import { Check } from '@element-plus/icons-vue'
import { adjustInventory } from '../../api/inventory'
import { useInventoryCommands } from '../../inventory/useInventoryCommands'
import QuantityInput from './QuantityInput.vue'

const props = defineProps<{ entryId: string; open: boolean }>()
const emit = defineEmits<{ close: []; completed: [] }>()
const targetQuantity = ref('0')
const reason = ref('')
const error = ref('')
const submitting = ref(false)
const intent = useInventoryCommands()

function changed() {
  intent.changed()
  error.value = ''
}

async function submit() {
  if (!reason.value.trim()) {
    error.value = '请输入调整原因'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    await intent.execute(key => adjustInventory(props.entryId, {
      targetQuantity: targetQuantity.value,
      reason: reason.value.trim(),
    }, key))
    intent.reset()
    emit('completed')
  } catch (cause) {
    const problem = cause as { detail?: string; code?: string; status?: number }
    error.value = problem.detail ?? problem.code ?? (problem.status === 403 ? '无权限执行库存操作' : '调整库存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="open" title="调整库存" width="min(34rem, calc(100vw - 2rem))" :teleported="false" :destroy-on-close="false" @close="emit('close')">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-form label-position="top" @submit.prevent="submit">
      <QuantityInput id="adjust-quantity" v-model="targetQuantity" label="调整后数量" @update:model-value="changed" />
      <el-form-item label="调整原因"><el-input id="adjust-reason" v-model="reason" @input="changed" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="emit('close')">取消</el-button><el-button type="primary" :icon="Check" :loading="submitting" @click="submit">{{ error ? '重试调整' : '确认调整' }}</el-button></template>
  </el-dialog>
</template>
