<script setup lang="ts">
import type { CatalogSearchItem, CategoryNode, ItemInput } from '../../catalog/catalogModels'
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
import ItemSearchResults from '../catalog/ItemSearchResults.vue'
import ItemForm from '../catalog/ItemForm.vue'

defineProps<{
  draft: ReceiveDraft
  results: CatalogSearchItem[]
  loading: boolean
  error: string | null
  categories: CategoryNode[]
  creating: boolean
  createPending: boolean
  canManageCategories?: boolean
}>()

const query = defineModel<string>('query', { required: true })
const creating = defineModel<boolean>('creating', { required: true })

defineEmits<{
  select: [item: CatalogSearchItem]
  create: [item: ItemInput]
  next: []
  back: []
}>()
</script>

<template>
  <section class="receive-step">
    <div class="receive-step__intro">
      <span>第 2 步</span>
      <h2>选择物品</h2>
      <p v-if="draft.item">已选择：{{ draft.item.name }}</p>
      <p v-else>输入物品名称或条码，从搜索结果中选择要入库的物品。</p>
    </div>
    <p v-if="error && creating" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <ItemForm
      v-if="creating && categories.length"
      :categories="categories"
      :initial="{ barcodes: draft.lastScan?.kind === 'PRODUCT_BARCODE' ? [draft.lastScan.value] : [] }"
      :pending="createPending"
      @save="$emit('create', $event)"
    />
    <div v-else-if="creating" class="st-feedback">
      <p>创建物品前需要先有分类。</p>
      <RouterLink v-if="canManageCategories" to="/admin/categories">先创建分类</RouterLink>
      <span v-else>请联系管理员先创建分类。</span>
    </div>
    <div v-else class="receive-item-picker">
      <label>搜索物品<input v-model="query" type="search" autocomplete="off" /></label>
      <p v-if="loading" role="status">正在搜索...</p>
      <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
      <ItemSearchResults
        :items="results"
        :searched="Boolean(query.trim()) && !loading"
        :loading="loading"
        @select="$emit('select', $event)"
      />
      <button class="st-button" type="button" @click="creating = true">创建新物品</button>
    </div>
    <div class="receive-step__actions">
      <button class="st-button" type="button" @click="$emit('back')">返回</button>
      <button v-if="creating" class="st-button" type="button" @click="creating = false">取消建档</button>
      <button class="st-button st-button--primary" type="button" :disabled="!draft.item" @click="$emit('next')">下一步</button>
    </div>
  </section>
</template>
