<script setup lang="ts">
import type { CatalogSearchItem } from '../../catalog/catalogModels'
import StEmptyState from '../StEmptyState.vue'

withDefaults(defineProps<{
  items: CatalogSearchItem[]
  searched: boolean
  loading?: boolean
}>(), { loading: false })

defineEmits<{
  select: [CatalogSearchItem]
}>()
</script>
<template><StEmptyState v-if="!loading&&!items.length" :title="searched?'没有找到物品':'还没有物品'" :description="searched?'换个名称或扫描准确条码试试':'创建第一个物品后，它会显示在这里。'"/><ul v-else-if="items.length" class="search-results"><li v-for="item in items" :key="item.id"><button @click="$emit('select',item)"><strong>{{ item.name }}</strong><span>{{ item.categoryPath }}</span><span>{{ [item.brand,item.specification].filter(Boolean).join(' · ') }}</span><small v-if="item.matchType==='BARCODE_EXACT'">条码精确匹配</small><small v-for="tag in item.tags" :key="tag">{{ tag }}</small></button></li></ul></template>
<style scoped>.search-results{list-style:none;padding:0;display:grid;gap:var(--st-space-3)}button{width:100%;display:grid;gap:4px;text-align:left;padding:var(--st-space-4);border:1px solid var(--st-color-border);border-radius:var(--st-radius-card);background:var(--st-color-surface)}small{color:var(--st-color-primary)}</style>
