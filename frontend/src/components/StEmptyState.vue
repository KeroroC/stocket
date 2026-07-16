<script setup lang="ts">
import { Box } from '@element-plus/icons-vue'

defineProps<{
  title: string
  description?: string
  iconLabel?: string
}>()
</script>

<template>
  <el-empty class="st-empty" :description="description || title">
    <template #image>
      <span class="st-empty__icon" aria-hidden="true"><el-icon><Box /></el-icon></span>
      <span v-if="iconLabel" class="st-visually-hidden">{{ iconLabel }}</span>
    </template>
    <template v-if="description" #description>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </template>
    <slot />
  </el-empty>
</template>

<style scoped>
.st-empty {
  padding: var(--st-space-6);
  border: 1px dashed color-mix(in srgb, var(--st-color-primary) 24%, var(--st-color-border));
  border-radius: var(--st-radius-card);
  background: color-mix(in srgb, var(--st-color-primary-soft) 18%, var(--st-color-surface));
}

h2,
p {
  margin: 0;
}

h2 {
  color: var(--st-color-text);
  font-size: 1.125rem;
}

.st-empty p {
  max-width: 32rem;
  margin-top: var(--st-space-2);
  color: var(--st-color-text-muted);
  line-height: 1.6;
}

.st-empty__icon {
  display: grid;
  width: 3.5rem;
  height: 3.5rem;
  place-items: center;
  border-radius: 50%;
  background: var(--st-color-primary-soft);
  color: var(--st-color-primary);
  font-size: 1.6rem;
}

.st-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
</style>
