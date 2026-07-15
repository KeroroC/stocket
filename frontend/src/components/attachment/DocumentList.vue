<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { deleteAttachment, downloadAttachment, listAttachments, type AttachmentSummary } from '../../api/attachment'
import AttachmentUploader from './AttachmentUploader.vue'
const props=defineProps<{ownerType:string;ownerId:string;canWrite:boolean}>();const attachments=ref<AttachmentSummary[]>([]);const error=ref('')
const documents=computed(()=>attachments.value.filter(item=>item.purpose==='INVOICE'||item.purpose==='WARRANTY'))
onMounted(load);async function load(){try{attachments.value=(await listAttachments(props.ownerType,props.ownerId))??[]}catch{error.value='文档加载失败'}}
function added(item:AttachmentSummary){attachments.value.unshift(item)}
async function download(item:AttachmentSummary){const blob=await downloadAttachment(item.id);const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=item.filename;link.click();URL.revokeObjectURL(url)}
async function remove(item:AttachmentSummary){try{await ElMessageBox.confirm(`确认删除 ${item.filename}？`,'删除文档',{type:'warning'})}catch{return}await deleteAttachment(item.id);attachments.value=attachments.value.filter(value=>value.id!==item.id)}
</script>
<template><el-card class="attachment-section" shadow="never"><template #header><h2>发票与保修</h2></template><el-alert v-if="error" :title="error" type="error" show-icon :closable="false"/><el-table v-if="documents.length" :data="documents" row-key="id"><el-table-column prop="filename" label="文件名" min-width="180"/><el-table-column label="用途" width="100"><template #default="{row}"><el-tag type="info">{{row.purpose==='INVOICE'?'发票':'保修'}}</el-tag></template></el-table-column><el-table-column label="操作" min-width="160"><template #default="{row}"><el-button link type="primary" @click="download(row as AttachmentSummary)">下载 {{row.filename}}</el-button><el-button v-if="canWrite" link type="danger" @click="remove(row as AttachmentSummary)">删除 {{row.filename}}</el-button></template></el-table-column></el-table><el-empty v-else description="暂无发票或保修文件"/><el-space v-if="canWrite" wrap><AttachmentUploader :owner-type="ownerType" :owner-id="ownerId" purpose="INVOICE" label="上传发票" @uploaded="added"/><AttachmentUploader :owner-type="ownerType" :owner-id="ownerId" purpose="WARRANTY" label="上传保修文件" @uploaded="added"/></el-space></el-card></template>
