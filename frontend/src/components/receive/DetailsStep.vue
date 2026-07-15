<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
import type { ReceiveLocationSelection } from '../../receive/ReceiveDraft'
const props = defineProps<{ draft: ReceiveDraft; locations: ReceiveLocationSelection[]; canManageLocations?: boolean }>()
const emit = defineEmits<{ update: [value: Partial<ReceiveDraft>]; selectLocation: [value: ReceiveLocationSelection]; next: []; back: []; scanLocation: [] }>()

function selectLocation(id: string) {
  const location = props.locations.find(candidate => candidate.id === id)
  if (location) emit('selectLocation', location)
}
</script>
<template><el-card class="receive-step" shadow="never"><div class="receive-step__intro"><span>第 3 步</span><h2>填写详情</h2><p class="receive-step__location">位置：{{ props.draft.location?.fullPath ?? props.draft.location?.name ?? '未选择' }}</p></div><el-form class="receive-step__fields" label-position="top"><el-form-item label="存放位置"><el-select :model-value="props.draft.location?.id ?? ''" filterable @update:model-value="selectLocation"><el-option v-for="location in locations" :key="location.id" :label="location.fullPath ?? location.name" :value="location.id" /></el-select></el-form-item><el-alert v-if="!locations.length" type="info" :closable="false"><template #title>暂无可用位置。<RouterLink v-if="canManageLocations" to="/admin/locations">先创建位置</RouterLink><span v-else>请联系管理员先创建位置。</span></template></el-alert><el-button @click="emit('scanLocation')">扫描位置码</el-button><el-form-item label="数量"><el-input :model-value="props.draft.quantity" inputmode="decimal" @update:model-value="emit('update', { quantity: String($event) })" /></el-form-item><el-form-item label="批次号"><el-input :model-value="props.draft.batchNumber" @update:model-value="emit('update', { batchNumber: String($event) })" /></el-form-item></el-form><div class="receive-step__actions"><el-button @click="emit('back')">返回</el-button><el-button type="primary" @click="emit('next')">下一步</el-button></div></el-card></template>
