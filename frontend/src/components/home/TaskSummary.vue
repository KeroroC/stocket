<script setup lang="ts">
defineProps<{
  title: string
  count: number
  tone: 'primary' | 'warning' | 'danger' | 'accent'
}>()
</script>
<template>
  <el-card :class="['task-summary', `task-summary--${tone}`]" shadow="hover">
    <span class="task-summary__indicator" aria-hidden="true" />
    <div>
      <strong>{{ count }}</strong>
      <h3>{{ title }}</h3>
    </div>
    <span class="task-summary__hint">查看详情</span>
  </el-card>
</template>
