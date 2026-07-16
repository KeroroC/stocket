<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
defineProps<{ draft: ReceiveDraft; current: string }>()
defineEmits<{
  submit: []
  back: []
}>()
</script>
<template><el-card class="receive-step" shadow="never"><div class="receive-step__intro"><span>第 4 步</span><h2>确认入库</h2><p>提交前核对库存变化和存放位置。</p></div><el-result icon="success" title="确认库存变化" :sub-title="`${current} + ${draft.quantity} = 提交后库存 · 位置：${draft.location?.fullPath ?? draft.location?.name}`"/><div class="receive-step__actions"><el-button @click="$emit('back')">返回</el-button><el-button type="primary" @click="$emit('submit')">确认入库</el-button></div></el-card></template>
