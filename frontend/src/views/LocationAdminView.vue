<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { createLocation, listLocations, type LocationNode } from '../api/location'
import StPageHeader from '../components/StPageHeader.vue'
import StEmptyState from '../components/StEmptyState.vue'
import LocationTree from '../components/location/LocationTree.vue'
import LocationEditor from '../components/location/LocationEditor.vue'

const nodes = ref<LocationNode[]>([])
const selected = ref<LocationNode>()
const editing = ref(false)
const error = ref('')

onMounted(load)

async function load() {
  try {
    nodes.value = await listLocations()
    selected.value = nodes.value[0]
  } catch (cause) {
    error.value = (cause as { detail?: string }).detail ?? '位置加载失败'
  }
}

function startCreating() {
  editing.value = true
}

async function save(data: Parameters<typeof createLocation>[0]) {
  try {
    const node = await createLocation(data)
    nodes.value.push(node)
    selected.value = node
    editing.value = false
    error.value = ''
  } catch (cause) {
    error.value = (cause as { detail?: string }).detail ?? '保存失败'
  }
}

async function copy() {
  if (selected.value) await navigator.clipboard.writeText(`stocket:location:${selected.value.publicCode}`)
}
</script>

<template>
  <section class="st-page">
    <StPageHeader title="位置管理" description="位置用于记录物品存放处；创建位置时系统会自动生成唯一、稳定的位置码。">
      <template #actions>
        <button class="st-button st-button--primary" type="button" @click="startCreating">{{ selected ? '添加子位置' : '创建位置' }}</button>
      </template>
    </StPageHeader>
    <p v-if="error" class="st-feedback st-feedback--error" role="alert">{{ error }}</p>
    <LocationEditor v-if="editing && !nodes.length" @save="save" />
    <StEmptyState v-else-if="!nodes.length" title="还没有位置" description="先创建第一个顶级位置，例如“家”或“仓库”。">
      <button class="st-button st-button--primary" type="button" @click="startCreating">创建第一个位置</button>
    </StEmptyState>
    <div v-else class="admin-grid">
      <LocationTree :nodes="nodes" :selected-id="selected?.id" @select="selected = $event" />
      <LocationEditor v-if="editing" :parent="selected" @save="save" />
      <article v-else-if="selected" class="admin-detail">
        <h2>{{ selected.name }}</h2>
        <p>{{ selected.fullPath }}</p>
        <h3>位置码</h3>
        <p>系统在创建位置时自动生成。它不会随位置改名而改变，可复制后制作成二维码贴在实物位置上。</p>
        <code>stocket:location:{{ selected.publicCode }}</code>
        <button class="st-button" type="button" @click="copy">复制位置码</button>
      </article>
    </div>
  </section>
</template>

<style scoped>.admin-detail button { margin-top: var(--st-space-4); }</style>
