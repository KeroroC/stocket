<script setup lang="ts">
import TaskSummary from './TaskSummary.vue'
import type { DashboardSummary } from '../../dashboard/DashboardSummary'
defineProps<{ summary: DashboardSummary }>()
</script>
<template>
  <section class="attention-section" aria-labelledby="attention-title">
    <div class="attention-section__header">
      <div>
        <p class="attention-section__eyebrow">库存提醒</p>
        <h2 id="attention-title">待关注事项</h2>
      </div>
      <span>{{ summary.openTotal }} 项待处理</span>
    </div>
    <div class="attention-list">
      <TaskSummary tone="warning" title="30 天内到期" :count="summary.expiring" />
      <TaskSummary tone="danger" title="已过期" :count="summary.expired" />
      <TaskSummary tone="accent" title="低库存" :count="summary.lowStock" />
      <TaskSummary tone="primary" title="待处理项目" :count="summary.openTotal" />
    </div>
  </section>
</template>
