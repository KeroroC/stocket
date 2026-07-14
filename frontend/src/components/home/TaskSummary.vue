<script setup lang="ts">
defineProps<{
  title: string
  count: number
}>()
</script>
<template><article class="task-summary"><h2>{{ title }}</h2><strong>{{ count }}</strong></article></template>
