<script setup lang="ts">
import type { ReceiveDraft } from '../../receive/ReceiveDraft'
import type { ReceiveLocationSelection } from '../../receive/ReceiveDraft'
const props = defineProps<{ draft: ReceiveDraft; locations: ReceiveLocationSelection[]; canManageLocations?: boolean }>()
const emit = defineEmits<{ update: [value: Partial<ReceiveDraft>]; selectLocation: [value: ReceiveLocationSelection]; next: []; back: []; scanLocation: [] }>()

function selectLocation(event: Event) {
  const id = (event.target as HTMLSelectElement).value
  const location = props.locations.find(candidate => candidate.id === id)
  if (location) emit('selectLocation', location)
}
</script>
<template><section class="receive-step"><div class="receive-step__intro"><span>第 3 步</span><h2>填写详情</h2><p class="receive-step__location">位置：{{ props.draft.location?.fullPath ?? props.draft.location?.name ?? '未选择' }}</p></div><div class="receive-step__fields"><label>存放位置<select :value="props.draft.location?.id ?? ''" @change="selectLocation"><option value="">请选择位置</option><option v-for="location in locations" :key="location.id" :value="location.id">{{ location.fullPath ?? location.name }}</option></select></label><p v-if="!locations.length" class="st-feedback">暂无可用位置。<RouterLink v-if="canManageLocations" to="/admin/locations">先创建位置</RouterLink><span v-else>请联系管理员先创建位置。</span></p><button class="st-button" type="button" @click="emit('scanLocation')">扫描位置码</button><label>数量<input :value="props.draft.quantity" inputmode="decimal" @input="emit('update', { quantity: ($event.target as HTMLInputElement).value })" /></label><label>批次号<input :value="props.draft.batchNumber" @input="emit('update', { batchNumber: ($event.target as HTMLInputElement).value })" /></label></div><div class="receive-step__actions"><button class="st-button" type="button" @click="emit('back')">返回</button><button class="st-button st-button--primary" type="button" @click="emit('next')">下一步</button></div></section></template>
