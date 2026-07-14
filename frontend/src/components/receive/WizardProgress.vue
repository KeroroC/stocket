<script setup lang="ts">
import { computed } from 'vue'
import type { ReceiveWizardKind } from '../../receive/ReceiveWizardState'
const props = defineProps<{ current: ReceiveWizardKind }>()
const steps = ['识别', '匹配', '详情', '确认']
const kinds: ReceiveWizardKind[] = ['IDENTIFY', 'MATCH', 'DETAILS', 'CONFIRM']
const currentIndex = computed(() => {
  if (['CONFIRM', 'SUBMITTING', 'CONFLICT'].includes(props.current)) return 3
  if (props.current === 'COMPLETED') return 4
  return kinds.indexOf(props.current)
})
</script>
<template><ol class="wizard-progress" aria-label="入库步骤"><li v-for="(step, index) in steps" :key="step" :class="{ active: currentIndex === index, completed: currentIndex > index }" :aria-current="currentIndex === index ? 'step' : undefined"><span>{{ index + 1 }}</span>{{ step }}</li></ol></template>
