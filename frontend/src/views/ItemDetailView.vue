<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { archiveItem, getItem } from '../api/catalog'
import type { ItemDefinition } from '../catalog/catalogModels'
import AttachmentGallery from '../components/attachment/AttachmentGallery.vue'
import DocumentList from '../components/attachment/DocumentList.vue'
import ExportDialog from '../components/export/ExportDialog.vue'
import StPageHeader from '../components/StPageHeader.vue'

const props=defineProps<{itemId:string;role:string}>();const item=ref<ItemDefinition>();const error=ref('')
const canWrite=computed(()=>props.role!=='VIEWER')
onMounted(async()=>{try{item.value=await getItem(props.itemId)}catch{error.value='物品加载失败'}})
async function archive(){if(item.value&&confirm('确认归档此物品？'))item.value=await archiveItem(item.value.id,item.value.version)}
</script>
<template><article v-if="item" class="item-detail"><StPageHeader :title="item.name" eyebrow="物品详情"><template #actions><ExportDialog kind="catalog" :filters="{q:item.name}" label="导出当前物品"/><button v-if="canWrite" class="st-button st-button--danger" @click="archive">归档物品</button></template></StPageHeader><dl><dt>单位</dt><dd>{{item.defaultUnit}}</dd><dt>条码</dt><dd><span v-for="code in item.barcodes" :key="code">{{code}}</span></dd><dt>标签</dt><dd><span v-for="tag in item.tags" :key="tag">{{tag}}</span></dd></dl><AttachmentGallery owner-type="ITEM_DEFINITION" :owner-id="item.id" :can-write="canWrite"/><DocumentList owner-type="ITEM_DEFINITION" :owner-id="item.id" :can-write="canWrite"/></article><p v-else-if="error" class="st-feedback st-feedback--error" role="alert">{{error}}</p><p v-else class="st-feedback">正在加载物品...</p></template>
