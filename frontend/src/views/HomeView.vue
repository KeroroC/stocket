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
<template>
  <section class="home-view">
    <header class="home-view__header">
      <div>
        <p class="home-view__eyebrow">家庭库存概览</p>
        <h1>今天需要关注什么？</h1>
        <p>快速找到物品、完成入库，并及时处理库存提醒。</p>
      </div>
      <div class="home-view__actions">
        <QuickReceive />
      </div>
    </header>

    <div class="home-view__search">
      <GlobalSearch />
    </div>
    <p v-if="error" class="home-view__error" role="alert">{{ error }}</p>
    <AttentionList :summary="summary" />
  </section>
</template>
