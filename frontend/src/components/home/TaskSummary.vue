<script setup lang="ts">
import { ArrowRight } from '@element-plus/icons-vue'

defineProps<{
  title: string
  count: number
  tone: 'primary' | 'warning' | 'danger' | 'accent'
  description: string
}>()
</script>
<template>
  <RouterLink
    :class="['task-summary', `task-summary--${tone}`]"
    to="/reminders"
    :aria-label="`${title} ${count} 项，查看提醒`"
  >
    <span class="task-summary__indicator" aria-hidden="true" />
    <span class="task-summary__content">
      <strong>{{ count }}</strong>
      <h3>{{ title }}</h3>
      <small>{{ description }}</small>
    </span>
    <span class="task-summary__hint">查看<ArrowRight aria-hidden="true" /></span>
  </RouterLink>
</template>
