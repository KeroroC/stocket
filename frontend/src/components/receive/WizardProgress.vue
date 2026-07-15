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
<template><el-steps class="wizard-progress" :active="currentIndex" finish-status="success" align-center aria-label="入库步骤"><el-step v-for="step in steps" :key="step" :title="step" /></el-steps></template>
