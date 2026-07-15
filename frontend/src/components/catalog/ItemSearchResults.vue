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
  <el-skeleton v-if="loading" :rows="5" animated />
  <StEmptyState
    v-else-if="!items.length"
    :title="searched ? '没有找到物品' : '还没有物品'"
    :description="searched ? '换个名称或扫描准确条码试试' : '创建第一个物品后，它会显示在这里。'"
  />
  <el-table v-else-if="isDesktop" :data="items" row-key="id" class="item-results-table">
    <el-table-column label="名称" min-width="180"><template #default="{ row }"><el-button link type="primary" @click="$emit('select', row as CatalogSearchItem)">{{ row.name }}</el-button></template></el-table-column>
    <el-table-column prop="categoryPath" label="分类" min-width="140" />
    <el-table-column label="规格" min-width="160"><template #default="{ row }">{{ [row.brand, row.specification].filter(Boolean).join(' · ') || '—' }}</template></el-table-column>
    <el-table-column label="匹配" min-width="160"><template #default="{ row }"><el-space wrap><el-tag v-if="row.matchType === 'BARCODE_EXACT'" type="success">条码精确匹配</el-tag><el-tag v-for="tag in row.tags" :key="tag" type="info">{{ tag }}</el-tag></el-space></template></el-table-column>
  </el-table>
  <ul v-else class="item-result-cards">
    <li v-for="item in items" :key="`card-${item.id}`">
      <button type="button" class="item-result-card" @click="$emit('select', item)">
        <span class="item-result-card__heading">
          <strong>{{ item.name }}</strong>
          <small>{{ item.categoryPath || '未分类' }}</small>
        </span>
        <span v-if="item.brand || item.specification" class="item-result-card__specification">
          {{ [item.brand, item.specification].filter(Boolean).join(' · ') }}
        </span>
        <span class="item-result-card__tags">
          <el-tag v-if="item.matchType === 'BARCODE_EXACT'" size="small" type="success">条码精确匹配</el-tag>
          <el-tag v-for="tag in item.tags" :key="tag" size="small" type="info">{{ tag }}</el-tag>
        </span>
      </button>
    </li>
  </ul>
</template>
<style scoped>
.item-result-cards {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: var(--st-space-3);
}
.item-result-card {
  width: 100%;
  display: grid;
  gap: var(--st-space-3);
  text-align: left;
  padding: var(--st-space-4);
  border: 1px solid var(--st-color-border);
  border-radius: var(--st-radius-card);
  background: var(--st-color-surface);
  color: var(--st-color-text);
  cursor: pointer;
  transition: border-color var(--st-motion-fast), background var(--st-motion-fast), transform var(--st-motion-fast);
}
.item-result-card:hover,
.item-result-card:focus-visible {
  border-color: var(--st-color-primary);
  background: var(--st-color-primary-soft);
  outline: 0;
  transform: translateY(-1px);
}
.item-result-card__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--st-space-3);
}
.item-result-card__heading strong {
  font-size: 1rem;
}
.item-result-card__heading small,
.item-result-card__specification {
  color: var(--st-color-text-muted);
}
.item-result-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--st-space-2);
}
</style>
