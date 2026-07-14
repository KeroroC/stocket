<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
defineProps<{ draft: ReceiveDraft; current: string }>()
defineEmits<{
  submit: []
  back: []
}>()
</script>
<template><section class="receive-step"><div class="receive-step__intro"><span>第 4 步</span><h2>确认入库</h2><p>提交前核对库存变化和存放位置。</p></div><div class="receive-preview"><strong>{{ current }} + {{ draft.quantity }} = 提交后库存</strong><p>位置：{{ draft.location?.name }}</p></div><div class="receive-step__actions"><button class="st-button" type="button" @click="$emit('back')">返回</button><button class="st-button st-button--primary" type="button" @click="$emit('submit')">确认入库</button></div></section></template>
