<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string; eyebrow?: string; headingLevel?: 1 | 2 }>(), { headingLevel: 1 })
</script>
<template><header class="st-page-header"><div><p v-if="eyebrow" class="st-page-header__eyebrow">{{ eyebrow }}</p><component :is="`h${headingLevel}`">{{ title }}</component><p v-if="description">{{ description }}</p></div><div v-if="$slots.actions" class="st-page-header__actions"><slot name="actions" /></div></header></template>
<style scoped>.st-page-header{display:flex;gap:var(--st-space-4);align-items:flex-start;justify-content:space-between;margin-bottom:var(--st-space-6)}h1,h2,p{margin:0}.st-page-header__eyebrow{color:var(--st-color-primary);font-weight:700}.st-page-header__actions{display:flex;gap:var(--st-space-2)}@media(max-width:640px){.st-page-header{flex-direction:column}}</style>
