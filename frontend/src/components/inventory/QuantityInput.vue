<script setup lang="ts">
defineProps<{ modelValue: string; label: string; id: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
</script>

<template>
  <el-form-item class="inventory-field" :label="label">
    <el-input
      :id="id"
      :model-value="modelValue"
      inputmode="decimal"
      autocomplete="off"
      @update:model-value="emit('update:modelValue', String($event))"
    />
  </el-form-item>
</template>
