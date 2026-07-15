<script setup lang="ts">
export type StStatus = 'healthy' | 'expiring' | 'expired' | 'low-stock' | 'archived'

defineProps<{
  status: StStatus
  label: string
}>()
</script>
<template><el-tag :type="status === 'healthy' ? 'success' : status === 'expired' ? 'danger' : status === 'archived' ? 'info' : 'warning'" effect="light" round><span :data-status="status">{{ label }}</span></el-tag></template>
