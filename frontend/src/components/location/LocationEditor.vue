<script setup lang="ts">
import { ref } from 'vue'
import type { LocationNode } from '../../api/location'
const props = defineProps<{ parent?: LocationNode }>()
const emit = defineEmits<{ save: [{ name: string; parentId: string | null }] }>()
const name = ref('')
</script>
<template><el-card class="location-editor" shadow="never"><el-form label-position="top" @submit.prevent="emit('save',{name,parentId:props.parent?.id??null})"><el-form-item label="位置名称"><el-input v-model="name" required /></el-form-item><el-button native-type="submit" type="primary">保存位置</el-button></el-form></el-card></template>
