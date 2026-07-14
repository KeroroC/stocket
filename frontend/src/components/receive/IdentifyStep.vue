<script setup lang="ts">
defineEmits<{
  next: []
  scan: []
}>()
</script>
<template><section class="receive-step"><div class="receive-step__intro"><span>第 1 步</span><h2>识别物品</h2><p>扫描商品条码，或继续手工选择。</p></div><div class="receive-step__actions"><button class="st-button st-button--primary" type="button" @click="$emit('scan')">扫描商品条码</button><button class="st-button" type="button" @click="$emit('next')">手工选择</button></div></section></template>
