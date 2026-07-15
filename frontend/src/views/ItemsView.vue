<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { createItem, listCategories } from '../api/catalog'
import { listLocations, type LocationNode } from '../api/location'
import { useCatalogSearch } from '../catalog/useCatalogSearch'
import type { CategoryNode, ItemInput } from '../catalog/catalogModels'
import StPageHeader from '../components/StPageHeader.vue'
import ItemSearchResults from '../components/catalog/ItemSearchResults.vue'
import ItemForm from '../components/catalog/ItemForm.vue'
import { useDesktopLayout } from '../composables/useDesktopLayout'
import ItemDetailView from './ItemDetailView.vue'

const props = defineProps<{ role: string }>()
const search = useCatalogSearch()
const creating = ref(false)
const selectedId = ref<string>()
const categories = ref<CategoryNode[]>([])
const locations = ref<LocationNode[]>([])
const browseMode = ref<'category' | 'location'>('category')
const browseExpanded = ref(false)
const error = ref('')
const canWrite = computed(() => props.role !== 'VIEWER')
const { isDesktop } = useDesktopLayout()
const browseTitle = computed(() => browseMode.value === 'category' ? '分类浏览' : '位置浏览')

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

function returnToList() {
  creating.value = false
  selectedId.value = undefined
  error.value = ''
}
</script>

<template>
  <section class="st-page catalog-page">
    <StPageHeader title="物品目录" description="统一管理物品定义、分类、规格与条码信息">
      <template v-if="canWrite && !creating" #actions><el-button type="primary" @click="creating = true; selectedId = undefined">创建物品</el-button></template>
    </StPageHeader>
    <div class="catalog-page__toolbar">
      <label for="catalog-search">搜索目录</label>
      <el-input id="catalog-search" v-model="search.query.value" type="search" aria-label="搜索物品" placeholder="输入名称、条码或标签" clearable />
      <p>支持物品名称与精确条码，输入后自动更新结果。</p>
    </div>
    <p v-if="search.error.value || error" class="st-feedback st-feedback--error" role="alert">{{ search.error.value || error }}</p>
    <div class="catalog-page__workspace">
      <aside class="catalog-page__browser" aria-labelledby="catalog-browser-title">
        <header>
          <div>
            <p>目录导航</p>
            <h2 id="catalog-browser-title">{{ browseTitle }}</h2>
          </div>
          <el-button
            class="catalog-page__browser-toggle"
            text
            :aria-expanded="isDesktop || browseExpanded"
            aria-controls="catalog-browser-content"
            @click="browseExpanded = !browseExpanded"
          >
            {{ browseExpanded ? '收起' : '展开' }}{{ browseMode === 'category' ? '分类' : '位置' }}浏览
          </el-button>
        </header>
        <div id="catalog-browser-content" v-show="isDesktop || browseExpanded" class="catalog-page__browser-content">
          <el-segmented v-model="browseMode" :options="[{ label: '按分类', value: 'category' }, { label: '按位置', value: 'location' }]" aria-label="浏览方式" />
          <el-scrollbar>
            <el-tree v-if="browseMode === 'category'" :data="categories" node-key="id" :props="{ label: 'name', children: 'children' }" aria-label="分类浏览" />
            <el-tree v-else :data="locations" node-key="id" :props="{ label: 'fullPath', children: 'children' }" aria-label="位置浏览" />
          </el-scrollbar>
        </div>
      </aside>
      <div class="catalog-page__main">
        <header v-if="creating || selectedId" class="catalog-page__context">
          <el-button @click="returnToList">返回物品列表</el-button>
          <div>
            <p>{{ creating ? '新增物品' : '物品详情' }}</p>
            <h2>{{ creating ? '创建目录条目' : '查看物品信息' }}</h2>
          </div>
        </header>
        <header v-else class="catalog-page__results-heading">
          <div>
            <p>{{ search.query.value.trim() ? '搜索结果' : '目录总览' }}</p>
            <h2>{{ search.query.value.trim() ? `“${search.query.value.trim()}”的匹配项` : '全部物品' }}</h2>
          </div>
          <span aria-live="polite">{{ search.results.value.length }} 项</span>
        </header>
        <ItemForm v-if="creating" :categories="categories" @save="save" />
        <ItemDetailView v-else-if="selectedId" :item-id="selectedId" :role="role" />
        <ItemSearchResults v-else :items="search.results.value" :searched="Boolean(search.query.value.trim())" :loading="search.loading.value" @select="selectedId = $event.id" />
      </div>
    </div>
  </section>
</template>
