<script setup lang="ts">
defineProps<{
  title: string
  count: number
  tone: 'primary' | 'warning' | 'danger' | 'accent'
}>()
</script>
<template>
  <article :class="['task-summary', `task-summary--${tone}`]">
    <span class="task-summary__indicator" aria-hidden="true" />
    <div>
      <strong>{{ count }}</strong>
      <h3>{{ title }}</h3>
    </div>
    <span class="task-summary__hint">查看详情</span>
  </article>
</template>
