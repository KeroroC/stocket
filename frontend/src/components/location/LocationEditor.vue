<script setup lang="ts">
import { ref } from 'vue'
import type { LocationNode } from '../../api/location'
const props = defineProps<{ parent?: LocationNode }>()
const emit = defineEmits<{ save: [{ name: string; parentId: string | null }] }>()
const name = ref('')
</script>
<template><form @submit.prevent="emit('save',{name,parentId:props.parent?.id??null})"><label>位置名称<input v-model="name" required /></label><button type="submit">保存位置</button></form></template>
