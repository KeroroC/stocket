<script setup lang="ts">
import { ref, watch } from 'vue'
import type { CategoryNode } from '../../catalog/catalogModels'

const props = defineProps<{ parent?: CategoryNode }>()
const emit = defineEmits<{ save: [{ name: string; parentId: string | null; defaultInventoryType: 'BATCH' | 'ASSET'; attributeSchema: [] }] }>()
const name = ref('')
const defaultInventoryType = ref<'BATCH' | 'ASSET'>('BATCH')

watch(() => props.parent, () => {
  name.value = ''
  defaultInventoryType.value = 'BATCH'
})
</script>

<template>
  <form class="st-editor" @submit.prevent="emit('save', { name, parentId: parent?.id ?? null, defaultInventoryType, attributeSchema: [] })">
    <label>分类名称<input v-model="name" required /></label>
    <label>默认库存类型
      <select v-model="defaultInventoryType"><option value="BATCH">批次</option><option value="ASSET">资产</option></select>
    </label>
    <fieldset><legend>属性模式</legend><p>创建后可添加 TEXT、NUMBER、BOOLEAN、DATE、ENUM 字段。</p></fieldset>
    <button class="st-button st-button--primary" type="submit">保存分类</button>
  </form>
</template>
