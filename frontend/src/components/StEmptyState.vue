<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  iconLabel?: string
}>()
</script>
<template><el-empty class="st-empty" :description="description || title"><template #image><span class="st-empty__icon" aria-hidden="true">○</span><span v-if="iconLabel" class="st-visually-hidden">{{ iconLabel }}</span></template><template v-if="description" #description><h2>{{ title }}</h2><p>{{ description }}</p></template><slot /></el-empty></template>
<style scoped>.st-empty{border:1px dashed var(--st-color-border);border-radius:var(--st-radius-card);background:var(--st-color-surface)}h2,p{margin:0}.st-empty p{margin-top:var(--st-space-2);color:var(--st-color-text-muted)}.st-empty__icon{font-size:2rem;color:var(--st-color-primary)}.st-visually-hidden{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}</style>
