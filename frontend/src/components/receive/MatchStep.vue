<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
defineProps<{ draft: ReceiveDraft }>()
defineEmits<{
  next: []
  back: []
}>()
</script>
<template><section><h2>匹配物品</h2><p>{{ draft.item?.name ?? '未匹配，可快速建档' }}</p><button type="button" @click="$emit('back')">返回</button><button type="button" @click="$emit('next')">下一步</button></section></template>
