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
  <el-card class="st-editor" shadow="never"><el-form label-position="top" @submit.prevent="emit('save', { name, parentId: parent?.id ?? null, defaultInventoryType, attributeSchema: [] })"><el-form-item label="分类名称"><el-input v-model="name" required /></el-form-item><el-form-item label="默认库存类型"><el-radio-group v-model="defaultInventoryType"><el-radio-button value="BATCH">批次</el-radio-button><el-radio-button value="ASSET">资产</el-radio-button></el-radio-group></el-form-item><el-alert title="创建后可添加 TEXT、NUMBER、BOOLEAN、DATE、ENUM 字段。" type="info" :closable="false" /><el-button native-type="submit" type="primary">保存分类</el-button></el-form></el-card>
</template>
