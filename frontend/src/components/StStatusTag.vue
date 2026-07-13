<script setup lang="ts">
export type StStatus = 'healthy' | 'expiring' | 'expired' | 'low-stock' | 'archived'

defineProps<{
  status: StStatus
  label: string
}>()
</script>
<template><span class="st-status" :data-status="status"><i aria-hidden="true" />{{ label }}</span></template>
<style scoped>.st-status{display:inline-flex;align-items:center;gap:6px;padding:4px 10px;border-radius:999px;background:var(--st-color-primary-soft);color:var(--st-color-primary);font-weight:600}.st-status i{width:7px;height:7px;border-radius:50%;background:currentColor}.st-status[data-status="expiring"],.st-status[data-status="low-stock"]{color:#8a5a0a;background:#fff1d6}.st-status[data-status="expired"]{color:var(--st-color-danger);background:#fde8e8}.st-status[data-status="archived"]{color:var(--st-color-text-muted);background:#eef0ec}</style>
