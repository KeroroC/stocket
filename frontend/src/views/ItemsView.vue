<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createItem, listCategories } from '../api/catalog'
import { listLocations, type LocationNode } from '../api/location'
import { useCatalogSearch } from '../catalog/useCatalogSearch'
import type { CategoryNode, ItemInput } from '../catalog/catalogModels'
import StPageHeader from '../components/StPageHeader.vue'
import ItemSearchResults from '../components/catalog/ItemSearchResults.vue'
import ItemForm from '../components/catalog/ItemForm.vue'
import ItemDetailView from './ItemDetailView.vue'

const props = defineProps<{ role: string }>()
const search = useCatalogSearch()
const creating = ref(false)
const selectedId = ref<string>()
const categories = ref<CategoryNode[]>([])
const locations = ref<LocationNode[]>([])
const browseMode = ref<'category' | 'location'>('category')
const error = ref('')
const canWrite = computed(() => props.role !== 'VIEWER')

onMounted(async () => {
  ;[categories.value, locations.value] = await Promise.all([listCategories(), listLocations()])
})

async function save(data: ItemInput) {
  try {
    const item = await createItem(data)
    creating.value = false
    selectedId.value = item.id
  } catch (cause) {
    error.value = (cause as { detail?: string; code?: string }).detail
      ?? (cause as { code?: string }).code ?? '保存失败'
  }
}
</script>

<template>
  <section class="st-page catalog-page">
    <StPageHeader title="物品目录" description="按名称或条码快速查找">
      <template v-if="canWrite" #actions><el-button type="primary" @click="creating = true">创建物品</el-button></template>
    </StPageHeader>
    <div class="catalog-page__toolbar st-toolbar">
      <el-segmented v-model="browseMode" :options="[{ label: '按分类', value: 'category' }, { label: '按位置', value: 'location' }]" aria-label="浏览方式" />
      <el-input v-model="search.query.value" type="search" aria-label="搜索物品" placeholder="搜索名称或条码" clearable />
    </div>
    <p v-if="search.error.value || error" class="st-feedback st-feedback--error" role="alert">{{ search.error.value || error }}</p>
    <div class="catalog-page__workspace">
      <el-card class="catalog-page__tree" shadow="never">
        <el-scrollbar>
          <el-tree v-if="browseMode === 'category'" :data="categories" node-key="id" :props="{ label: 'name', children: 'children' }" aria-label="分类浏览" />
          <el-tree v-else :data="locations" node-key="id" :props="{ label: 'fullPath', children: 'children' }" aria-label="位置浏览" />
        </el-scrollbar>
      </el-card>
      <div class="catalog-page__main">
        <ItemForm v-if="creating" :categories="categories" @save="save" />
        <ItemDetailView v-else-if="selectedId" :item-id="selectedId" :role="role" />
        <ItemSearchResults v-else :items="search.results.value" :searched="Boolean(search.query.value.trim())" :loading="search.loading.value" @select="selectedId = $event.id" />
      </div>
    </div>
  </section>
</template>
