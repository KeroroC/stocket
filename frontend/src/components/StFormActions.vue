<script setup lang="ts">
const props = withDefaults(defineProps<{ primaryLabel: string; secondaryLabel?: string; pending?: boolean; destructive?: boolean }>(), { pending: false, destructive: false })
const emit = defineEmits<{ submit: []; secondary: [] }>()
function submit() { if (!props.pending) emit('submit') }
</script>
<template><div class="st-form-actions"><button v-if="secondaryLabel" type="button" class="st-secondary" @click="$emit('secondary')">{{ secondaryLabel }}</button><button type="button" class="st-primary" :class="{ destructive }" :disabled="pending" @click="submit">{{ pending ? `正在${primaryLabel}` : primaryLabel }}</button></div></template>
<style scoped>.st-form-actions{display:flex;justify-content:flex-end;gap:var(--st-space-3)}button{min-height:var(--st-control-min-size);padding:0 var(--st-space-4);border-radius:var(--st-radius-control);font:inherit}.st-primary{border:0;color:white;background:var(--st-color-primary)}.st-primary.destructive{background:var(--st-color-danger)}.st-secondary{border:1px solid var(--st-color-border);background:var(--st-color-surface)}button:disabled{opacity:.55}@media(max-width:640px){.st-form-actions[data-sticky="true"]{position:sticky;bottom:0;padding:var(--st-space-3);background:var(--st-color-surface)}.st-form-actions button{flex:1}}</style>
