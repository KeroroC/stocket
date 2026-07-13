<script setup lang="ts">
import type { CategoryNode } from '../../catalog/catalogModels'
defineProps<{ nodes: CategoryNode[]; selectedId?: string }>()
defineEmits<{ select: [CategoryNode] }>()
const depth = (node: CategoryNode, nodes: CategoryNode[]) => { let d = 0; let p = node.parentId; while (p) { d++; p = nodes.find(n => n.id === p)?.parentId ?? null } return d }
</script>
<template><ul class="st-tree"><li v-for="node in nodes" :key="node.id"><button :aria-pressed="selectedId===node.id" :style="{paddingInlineStart:`${12+depth(node,nodes)*20}px`}" @click="$emit('select',node)">{{ node.name }}<span v-if="node.archived">（已归档）</span></button></li></ul></template>
<style scoped>.st-tree{list-style:none;margin:0;padding:0}.st-tree button{width:100%;min-height:44px;text-align:left;border:0;border-radius:var(--st-radius-control);background:transparent}.st-tree button[aria-pressed="true"]{background:var(--st-color-primary-soft);color:var(--st-color-primary)}</style>
