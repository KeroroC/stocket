<script setup lang="ts">
defineProps<{ modelValue: string; label: string; id: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <label class="inventory-field" :for="id">
    <span>{{ label }}</span>
    <input
      :id="id"
      :value="modelValue"
      inputmode="decimal"
      autocomplete="off"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </label>
</template>
