<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { deleteAttachment, downloadAttachment, listAttachments, type AttachmentSummary } from '../../api/attachment'
import AttachmentUploader from './AttachmentUploader.vue'

const props=defineProps<{ownerType:string;ownerId:string;canWrite:boolean}>()
const attachments=ref<AttachmentSummary[]>([]); const error=ref('')
const images=computed(()=>attachments.value.filter(item=>item.purpose==='COVER_IMAGE'||item.purpose==='ITEM_IMAGE'))
onMounted(load)
async function load(){try{attachments.value=(await listAttachments(props.ownerType,props.ownerId))??[]}catch{error.value='图片附件加载失败'}}
async function remove(item:AttachmentSummary){if(!confirm(`确认删除 ${item.filename}？`))return;await deleteAttachment(item.id);attachments.value=attachments.value.filter(value=>value.id!==item.id)}
async function download(item:AttachmentSummary){const blob=await downloadAttachment(item.id);const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=item.filename;link.click();URL.revokeObjectURL(url)}
function added(item:AttachmentSummary){if(item.purpose==='COVER_IMAGE')attachments.value=attachments.value.filter(value=>value.purpose!=='COVER_IMAGE');attachments.value.unshift(item)}
</script>
<template><section class="attachment-section"><h2>图片</h2><p v-if="error" role="alert">{{error}}</p><ul v-if="images.length" class="attachment-list"><li v-for="item in images" :key="item.id"><span>{{item.filename}}</span><span>{{item.purpose==='COVER_IMAGE'?'封面':'图片'}}</span><button type="button" @click="download(item)">下载 {{item.filename}}</button><button v-if="canWrite" type="button" @click="remove(item)">删除 {{item.filename}}</button></li></ul><p v-else>暂无图片</p><template v-if="canWrite"><AttachmentUploader :owner-type="ownerType" :owner-id="ownerId" purpose="ITEM_IMAGE" label="上传图片" @uploaded="added"/><AttachmentUploader :owner-type="ownerType" :owner-id="ownerId" purpose="COVER_IMAGE" label="替换封面" @uploaded="added"/></template></section></template>
