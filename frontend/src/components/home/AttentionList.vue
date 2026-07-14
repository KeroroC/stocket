<script setup lang="ts">
import TaskSummary from './TaskSummary.vue'
import type { DashboardSummary } from '../../dashboard/DashboardSummary'
defineProps<{ summary: DashboardSummary }>()
</script>
<template><section class="attention-list"><TaskSummary title="30 天内到期" :count="summary.expiring"/><TaskSummary title="已过期" :count="summary.expired"/><TaskSummary title="低库存" :count="summary.lowStock"/><TaskSummary title="待处理项目" :count="summary.openTotal"/></section></template>
