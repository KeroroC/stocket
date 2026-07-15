<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createCategory, listCategories } from '../api/catalog'
import type { CategoryNode } from '../catalog/catalogModels'
import StPageHeader from '../components/StPageHeader.vue'
import StEmptyState from '../components/StEmptyState.vue'
import CategoryTree from '../components/catalog/CategoryTree.vue'
import CategoryEditor from '../components/catalog/CategoryEditor.vue'

const nodes = ref<CategoryNode[]>([])
const selected = ref<CategoryNode>()
const editing = ref(false)
const error = ref('')

onMounted(load)

async function load() {
  try {
    nodes.value = await listCategories()
    selected.value = nodes.value[0]
  } catch (cause) {
    error.value = (cause as { detail?: string }).detail ?? '分类加载失败'
  }
}

function startCreating() {
  editing.value = true
}

async function save(data: Parameters<typeof createCategory>[0]) {
  try {
    const node = await createCategory(data)
    nodes.value.push(node)
    selected.value = node
    editing.value = false
    error.value = ''
  } catch (cause) {
    error.value = (cause as { detail?: string; code?: string }).detail
      ?? (cause as { code?: string }).code
      ?? '保存失败'
  }
}
</script>

<template>
  <section class="st-page">
    <StPageHeader title="分类管理" description="分类用于整理物品；创建物品前至少需要一个分类。">
      <template #actions>
        <button class="st-button st-button--primary" type="button" @click="startCreating">{{ selected ? '添加子分类' : '创建分类' }}</button>
      </template>
    </StPageHeader>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <CategoryEditor v-if="editing && !nodes.length" @save="save" />
    <StEmptyState v-else-if="!nodes.length" title="还没有分类" description="先创建第一个顶级分类，之后可继续添加子分类。">
      <button class="st-button st-button--primary" type="button" @click="startCreating">创建第一个分类</button>
    </StEmptyState>
    <div v-else class="admin-grid">
      <CategoryTree :nodes="nodes" :selected-id="selected?.id" @select="selected = $event" />
      <CategoryEditor v-if="editing" :parent="selected" @save="save" />
      <article v-else-if="selected" class="admin-detail">
        <h2>{{ selected.name }}</h2>
        <p>属性字段：{{ selected.attributeSchema.length }}</p>
      </article>
    </div>
  </section>
</template>
