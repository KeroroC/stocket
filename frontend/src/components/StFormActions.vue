<script setup lang="ts">
const props = withDefaults(defineProps<{ primaryLabel: string; secondaryLabel?: string; pending?: boolean; destructive?: boolean }>(), { pending: false, destructive: false })
const emit = defineEmits<{ submit: []; secondary: [] }>()
function submit() { if (!props.pending) emit('submit') }
</script>
<template><el-space class="st-form-actions"><el-button v-if="secondaryLabel" @click="$emit('secondary')">{{ secondaryLabel }}</el-button><el-button :type="destructive ? 'danger' : 'primary'" :loading="pending" :disabled="pending" @click="submit">{{ pending ? `正在${primaryLabel}` : primaryLabel }}</el-button></el-space></template>
<style scoped>.st-form-actions{display:flex;justify-content:flex-end}@media(max-width:640px){.st-form-actions[data-sticky="true"]{position:sticky;bottom:0;padding:var(--st-space-3);background:var(--st-color-surface)}}</style>
