<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
const props = defineProps<{ draft: ReceiveDraft }>()
const emit = defineEmits<{ update: [value: Partial<ReceiveDraft>]; next: []; back: [] }>()
</script>
<template><section><h2>填写详情</h2><label>数量<input :value="props.draft.quantity" inputmode="decimal" @input="emit('update', { quantity: ($event.target as HTMLInputElement).value })" /></label><label>批次号<input :value="props.draft.batchNumber" @input="emit('update', { batchNumber: ($event.target as HTMLInputElement).value })" /></label><button type="button" @click="emit('back')">返回</button><button type="button" @click="emit('next')">下一步</button></section></template>
