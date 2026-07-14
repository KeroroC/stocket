<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
const props = defineProps<{ draft: ReceiveDraft }>()
const emit = defineEmits<{ update: [value: Partial<ReceiveDraft>]; next: []; back: []; scanLocation: [] }>()
</script>
<template><section class="receive-step"><div class="receive-step__intro"><span>第 3 步</span><h2>填写详情</h2><p class="receive-step__location">位置：{{ props.draft.location?.name ?? '未选择' }}</p></div><div class="receive-step__fields"><button class="st-button" type="button" @click="emit('scanLocation')">扫描位置码</button><label>数量<input :value="props.draft.quantity" inputmode="decimal" @input="emit('update', { quantity: ($event.target as HTMLInputElement).value })" /></label><label>批次号<input :value="props.draft.batchNumber" @input="emit('update', { batchNumber: ($event.target as HTMLInputElement).value })" /></label></div><div class="receive-step__actions"><button class="st-button" type="button" @click="emit('back')">返回</button><button class="st-button st-button--primary" type="button" @click="emit('next')">下一步</button></div></section></template>
