<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { deleteAttachment, downloadAttachment, listAttachments, type AttachmentSummary } from '../../api/attachment'
import AttachmentUploader from './AttachmentUploader.vue'
const props=defineProps<{ownerType:string;ownerId:string;canWrite:boolean}>();const attachments=ref<AttachmentSummary[]>([]);const error=ref('')
const documents=computed(()=>attachments.value.filter(item=>item.purpose==='INVOICE'||item.purpose==='WARRANTY'))
onMounted(load);async function load(){try{attachments.value=(await listAttachments(props.ownerType,props.ownerId))??[]}catch{error.value='文档加载失败'}}
function added(item:AttachmentSummary){attachments.value.unshift(item)}
async function download(item:AttachmentSummary){const blob=await downloadAttachment(item.id);const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=item.filename;link.click();URL.revokeObjectURL(url)}
async function remove(item:AttachmentSummary){if(!confirm(`确认删除 ${item.filename}？`))return;await deleteAttachment(item.id);attachments.value=attachments.value.filter(value=>value.id!==item.id)}
</script>
<template><section class="attachment-section"><h2>发票与保修</h2><p v-if="error" class="st-feedback st-feedback--error" role="alert">{{error}}</p><ul v-if="documents.length" class="attachment-list"><li v-for="item in documents" :key="item.id"><span>{{item.filename}}</span><span>{{item.purpose==='INVOICE'?'发票':'保修'}}</span><button class="st-button" type="button" @click="download(item)">下载 {{item.filename}}</button><button v-if="canWrite" class="st-button st-button--danger" type="button" @click="remove(item)">删除 {{item.filename}}</button></li></ul><p v-else>暂无发票或保修文件</p><div v-if="canWrite" class="document-uploaders"><AttachmentUploader :owner-type="ownerType" :owner-id="ownerId" purpose="INVOICE" label="上传发票" @uploaded="added"/><AttachmentUploader :owner-type="ownerType" :owner-id="ownerId" purpose="WARRANTY" label="上传保修文件" @uploaded="added"/></div></section></template>
