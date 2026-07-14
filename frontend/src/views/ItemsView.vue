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
      <template v-if="canWrite" #actions><button class="st-button st-button--primary" @click="creating = true">创建物品</button></template>
    </StPageHeader>
    <div class="browse-switch" role="group" aria-label="浏览方式">
      <button type="button" :aria-pressed="browseMode === 'category'" @click="browseMode = 'category'">按分类</button>
      <button type="button" :aria-pressed="browseMode === 'location'" @click="browseMode = 'location'">按位置</button>
    </div>
    <ul v-if="browseMode === 'category'" class="browse-list" aria-label="分类浏览"><li v-for="category in categories" :key="category.id">{{ category.name }}</li></ul>
    <ul v-else class="browse-list" aria-label="位置浏览"><li v-for="location in locations" :key="location.id">{{ location.fullPath }}</li></ul>
    <label class="search-label">搜索物品<input v-model="search.query.value" type="search" aria-label="搜索物品" /></label>
    <p v-if="search.error.value || error" class="st-feedback st-feedback--error" role="alert">{{ search.error.value || error }}</p>
    <ItemForm v-if="creating" :categories="categories" @save="save" />
    <ItemDetailView v-else-if="selectedId" :item-id="selectedId" :role="role" />
    <ItemSearchResults v-else :items="search.results.value" :searched="Boolean(search.query.value.trim())" @select="selectedId = $event.id" />
  </section>
</template>
