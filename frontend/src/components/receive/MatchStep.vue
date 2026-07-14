<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
defineProps<{ draft: ReceiveDraft }>()
defineEmits<{
  next: []
  back: []
}>()
</script>
<template><section class="receive-step"><div class="receive-step__intro"><span>第 2 步</span><h2>匹配物品</h2><p>{{ draft.item?.name ?? '未匹配，可快速建档' }}</p></div><div class="receive-step__actions"><button class="st-button" type="button" @click="$emit('back')">返回</button><button class="st-button st-button--primary" type="button" @click="$emit('next')">下一步</button></div></section></template>
