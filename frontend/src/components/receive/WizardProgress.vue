<script setup lang="ts">
import type { ReceiveWizardKind } from '../../receive/ReceiveWizardState'
defineProps<{ current: ReceiveWizardKind }>()
const steps = ['识别', '匹配', '详情', '确认']
</script>
<template><ol class="wizard-progress" aria-label="入库步骤"><li v-for="step in steps" :key="step">{{ step }}</li></ol></template>
