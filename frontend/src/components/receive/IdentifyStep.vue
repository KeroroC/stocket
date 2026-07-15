<script setup lang="ts">
defineEmits<{
  next: []
  scan: []
}>()
</script>
<template><el-card class="receive-step" shadow="never"><div class="receive-step__intro"><span>第 1 步</span><h2>识别物品</h2><p>扫描商品条码，或继续手工选择。</p></div><div class="receive-step__actions"><el-button type="primary" @click="$emit('scan')">扫描商品条码</el-button><el-button @click="$emit('next')">手工选择</el-button></div></el-card></template>
