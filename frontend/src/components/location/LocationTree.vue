<script setup lang="ts">
import type { LocationNode } from '../../api/location'
defineProps<{ nodes: LocationNode[]; selectedId?: string }>()
defineEmits<{ select: [LocationNode] }>()
</script>
<template><el-tree class="st-tree" :data="nodes" node-key="id" :current-node-key="selectedId" highlight-current :props="{ label: 'fullPath', children: 'children' }" @node-click="$emit('select', $event as LocationNode)" /></template>
