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
  <section v-if="open" class="inventory-sheet" role="dialog" aria-label="调整库存">
    <header><h2>调整库存</h2><button type="button" aria-label="关闭调整库存" @click="emit('close')">关闭</button></header>
    <p v-if="error" role="alert">{{ error }}</p>
    <form @submit.prevent="submit">
      <QuantityInput id="adjust-quantity" v-model="targetQuantity" label="调整后数量" @update:model-value="changed" />
      <label for="adjust-reason">调整原因</label>
      <input id="adjust-reason" v-model="reason" @input="changed" />
      <button type="submit" :disabled="submitting"><Check aria-hidden="true" />{{ error ? '重试调整' : '确认调整' }}</button>
    </form>
  </section>
</template>
