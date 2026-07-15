<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { archiveItem, getItem } from '../api/catalog'
import type { ItemDefinition } from '../catalog/catalogModels'
import AttachmentGallery from '../components/attachment/AttachmentGallery.vue'
import DocumentList from '../components/attachment/DocumentList.vue'
import ExportDialog from '../components/export/ExportDialog.vue'
import StPageHeader from '../components/StPageHeader.vue'

const props=defineProps<{itemId:string;role:string}>();const item=ref<ItemDefinition>();const error=ref('')
const canWrite=computed(()=>props.role!=='VIEWER')
onMounted(async()=>{try{item.value=await getItem(props.itemId)}catch{error.value='物品加载失败'}})
async function archive(){if(!item.value)return;try{await ElMessageBox.confirm('确认归档此物品？','归档物品',{type:'warning'})}catch{return}item.value=await archiveItem(item.value.id,item.value.version)}
</script>
<template><article v-if="item" class="item-detail"><StPageHeader :title="item.name" eyebrow="物品详情"><template #actions><ExportDialog kind="catalog" :filters="{q:item.name}" label="导出当前物品"/><el-button v-if="canWrite" type="danger" @click="archive">归档物品</el-button></template></StPageHeader><el-descriptions :column="1" border><el-descriptions-item label="单位">{{item.defaultUnit}}</el-descriptions-item><el-descriptions-item label="条码"><el-space wrap><el-tag v-for="code in item.barcodes" :key="code">{{code}}</el-tag></el-space></el-descriptions-item><el-descriptions-item label="标签"><el-space wrap><el-tag v-for="tag in item.tags" :key="tag" type="info">{{tag}}</el-tag></el-space></el-descriptions-item></el-descriptions><AttachmentGallery owner-type="ITEM_DEFINITION" :owner-id="item.id" :can-write="canWrite"/><DocumentList owner-type="ITEM_DEFINITION" :owner-id="item.id" :can-write="canWrite"/></article><el-alert v-else-if="error" :title="error" type="error" show-icon :closable="false"/><el-skeleton v-else :rows="5" animated/></template>
