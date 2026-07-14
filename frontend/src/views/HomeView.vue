<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDashboard } from '../api/dashboard'
import type { DashboardSummary } from '../dashboard/DashboardSummary'
import GlobalSearch from '../components/search/GlobalSearch.vue'
import QuickReceive from '../components/home/QuickReceive.vue'
import AttentionList from '../components/home/AttentionList.vue'

const summary = ref<DashboardSummary>({ expiring: 0, expired: 0, lowStock: 0, openTotal: 0 })
const error = ref('')
onMounted(async () => {
  try { summary.value = (await getDashboard()).summary }
  catch { error.value = '首页加载失败，请稍后重试。' }
})
</script>
<template><section class="home-view"><GlobalSearch/><QuickReceive/><p v-if="error" role="alert">{{ error }}</p><AttentionList :summary="summary"/></section></template>
