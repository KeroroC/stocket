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
  <section v-if="open" class="inventory-sheet" role="dialog" aria-label="消耗库存">
    <header><h2>消耗库存</h2><button type="button" aria-label="关闭消耗库存" @click="emit('close')">关闭</button></header>
    <p v-if="error" role="alert">{{ error }}</p>
    <form @submit.prevent="submit">
      <QuantityInput id="consume-quantity" v-model="quantity" label="消耗数量" @update:model-value="changed" />
      <button type="submit" :disabled="submitting"><Check aria-hidden="true" />{{ error ? '重试消耗' : '确认消耗' }}</button>
    </form>
  </section>
</template>
