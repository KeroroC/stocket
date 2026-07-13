<script setup lang="ts">
import type { LocationNode } from '../../api/location'
defineProps<{ nodes: LocationNode[]; selectedId?: string }>()
defineEmits<{ select: [LocationNode] }>()
</script>
<template><ul class="st-tree"><li v-for="node in nodes" :key="node.id"><button :aria-pressed="selectedId===node.id" @click="$emit('select',node)">{{ node.fullPath }}</button></li></ul></template>
<style scoped>.st-tree{list-style:none;margin:0;padding:0}.st-tree button{width:100%;min-height:44px;text-align:left;border:0;border-radius:var(--st-radius-control);background:transparent}.st-tree button[aria-pressed="true"]{background:var(--st-color-primary-soft)}</style>
