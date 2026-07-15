<script setup lang="ts">
import type { CategoryNode } from '../../catalog/catalogModels'
defineProps<{ nodes: CategoryNode[]; selectedId?: string }>()
defineEmits<{ select: [CategoryNode] }>()
</script>
<template><el-tree class="st-tree" :data="nodes" node-key="id" :current-node-key="selectedId" highlight-current :props="{ label: 'name', children: 'children' }" @node-click="$emit('select', $event as CategoryNode)"><template #default="{ data }"><span>{{ data.name }}<el-tag v-if="data.archived" size="small" type="info">已归档</el-tag></span></template></el-tree></template>
