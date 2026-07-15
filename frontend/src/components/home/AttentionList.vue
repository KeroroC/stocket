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
      <RouterLink class="attention-section__total" to="/reminders">
        {{ summary.openTotal }} 项待处理
      </RouterLink>
    </div>
    <div class="attention-list">
      <TaskSummary tone="warning" title="30 天内到期" description="未来 30 天" :count="summary.expiring" />
      <TaskSummary tone="danger" title="已过期" description="建议优先处理" :count="summary.expired" />
      <TaskSummary tone="accent" title="低库存" description="及时安排补充" :count="summary.lowStock" />
      <TaskSummary tone="primary" title="待处理项目" description="查看全部提醒" :count="summary.openTotal" />
    </div>
  </section>
</template>
