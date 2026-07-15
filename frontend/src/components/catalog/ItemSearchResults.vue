<script setup lang="ts">
import type { CatalogSearchItem } from '../../catalog/catalogModels'
import StEmptyState from '../StEmptyState.vue'
import { useDesktopLayout } from '../../composables/useDesktopLayout'

const { isDesktop } = useDesktopLayout()

withDefaults(defineProps<{
  items: CatalogSearchItem[]
  searched: boolean
  loading?: boolean
}>(), { loading: false })

defineEmits<{
  select: [CatalogSearchItem]
}>()
</script>
<template>
  <StEmptyState
    v-if="!loading && !items.length"
    :title="searched ? '没有找到物品' : '还没有物品'"
    :description="searched ? '换个名称或扫描准确条码试试' : '创建第一个物品后，它会显示在这里。'"
  />
  <el-table v-else-if="isDesktop && items.length" :data="items" row-key="id" class="item-results-table">
    <el-table-column label="名称" min-width="180"><template #default="{ row }"><el-button link type="primary" @click="$emit('select', row as CatalogSearchItem)">{{ row.name }}</el-button></template></el-table-column>
    <el-table-column prop="categoryPath" label="分类" min-width="140" />
    <el-table-column label="规格" min-width="160"><template #default="{ row }">{{ [row.brand, row.specification].filter(Boolean).join(' · ') || '—' }}</template></el-table-column>
    <el-table-column label="匹配" min-width="160"><template #default="{ row }"><el-space wrap><el-tag v-if="row.matchType === 'BARCODE_EXACT'" type="success">条码精确匹配</el-tag><el-tag v-for="tag in row.tags" :key="tag" type="info">{{ tag }}</el-tag></el-space></template></el-table-column>
  </el-table>
  <ul v-if="!isDesktop && items.length" class="search-results">
    <li v-for="item in items" :key="`card-${item.id}`">
      <el-card shadow="hover" tabindex="0" @click="$emit('select', item)" @keydown.enter="$emit('select', item)">
        <el-button link type="primary" @click.stop="$emit('select', item)"><strong>{{ item.name }}</strong></el-button>
        <span>{{ item.categoryPath }}</span>
        <span>{{ [item.brand, item.specification].filter(Boolean).join(' · ') }}</span>
        <small v-if="item.matchType === 'BARCODE_EXACT'">条码精确匹配</small>
        <small v-for="tag in item.tags" :key="tag">{{ tag }}</small>
      </el-card>
    </li>
  </ul>
</template>
<style scoped>
.search-results {
  list-style: none;
  padding: 0;
  display: grid;
  gap: var(--st-space-3);
}
.search-results button {
  width: 100%;
  display: grid;
  gap: 4px;
  text-align: left;
  padding: var(--st-space-4);
  border: 1px solid var(--st-color-border);
  border-radius: var(--st-radius-card);
  background: var(--st-color-surface);
}
.search-results small {
  color: var(--st-color-primary);
}
</style>
