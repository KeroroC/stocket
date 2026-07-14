<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
defineProps<{ draft: ReceiveDraft; current: string }>()
defineEmits<{
  submit: []
  back: []
}>()
</script>
<template><section><h2>确认入库</h2><p>{{ current }} + {{ draft.quantity }} = 提交后库存</p><p>位置：{{ draft.location?.name }}</p><button type="button" @click="$emit('back')">返回</button><button type="button" @click="$emit('submit')">确认入库</button></section></template>
