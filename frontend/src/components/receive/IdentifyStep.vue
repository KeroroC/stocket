<script setup lang="ts">
defineEmits<{
  next: []
}>()
</script>
<template><section><h2>识别物品</h2><p>扫描商品条码，或继续手工选择。</p><button type="button" @click="$emit('next')">手工选择</button></section></template>
