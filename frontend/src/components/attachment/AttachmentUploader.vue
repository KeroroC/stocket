<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { uploadAttachment, type AttachmentSummary } from '../../api/attachment'

const props = withDefaults(defineProps<{ ownerType:string; ownerId:string; purpose:string; label?:string }>(), { label:'上传附件' })
const emit = defineEmits<{ uploaded:[attachment:AttachmentSummary] }>()
const selected = ref<File>(); const previewUrl = ref(''); const progress = ref(0); const pending = ref(false); const error = ref('')
let controller: AbortController | undefined
const accept = computed(() => props.purpose === 'COVER_IMAGE' || props.purpose === 'ITEM_IMAGE'
  ? 'image/jpeg,image/png,image/webp' : 'image/jpeg,image/png,image/webp,application/pdf')

function choose(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  error.value = ''
  if (file.size > 20 * 1024 * 1024) { error.value = '单个文件不能超过 20 MiB'; return }
  if (!accept.value.split(',').includes(file.type)) { error.value = '请选择 JPEG、PNG、WebP 或 PDF 文件'; return }
  revoke(); selected.value = file
  if (file.type.startsWith('image/')) previewUrl.value = URL.createObjectURL(file)
}

function chooseUpload(file: UploadFile) {
  if (!file.raw) return
  choose({ target: { files: [file.raw] } } as unknown as Event)
}

async function upload() {
  if (!selected.value) return
  if (props.purpose === 'COVER_IMAGE') {
    try { await ElMessageBox.confirm('确认替换当前封面？', '替换封面', { type: 'warning' }) }
    catch { return }
  }
  pending.value = true; error.value = ''; progress.value = 0; controller = new AbortController()
  try {
    const uploaded = await uploadAttachment(props.ownerType, props.ownerId, props.purpose, selected.value,
      { signal:controller.signal, onProgress:value => progress.value=value })
    emit('uploaded', uploaded); selected.value = undefined; progress.value = 100; revoke()
  } catch (problem) {
    const api = problem as { status?:number; code?:string; detail?:string }
    if (api.status === 401) { selected.value = undefined; revoke() }
    if (api.code !== 'UPLOAD_CANCELLED') error.value = api.detail ?? '上传失败，请重试'
  } finally { pending.value = false; controller = undefined }
}
function cancel() { controller?.abort() }
function revoke() { if (previewUrl.value) URL.revokeObjectURL(previewUrl.value); previewUrl.value='' }
onBeforeUnmount(revoke)
</script>

<template>
  <div class="attachment-uploader">
    <el-upload :auto-upload="false" :show-file-list="false" :accept="accept" :on-change="chooseUpload"><el-button>{{ label }}</el-button></el-upload>
    <p class="attachment-hint">服务端会再次验证文件内容，最大 20 MiB。</p>
    <img v-if="previewUrl" :src="previewUrl" alt="待上传预览" class="attachment-preview" />
    <p v-if="selected">{{ selected.name }}</p>
    <el-progress v-if="pending" :percentage="progress" />
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-button v-if="selected && !pending" type="primary" @click="upload">开始上传</el-button>
    <el-button v-if="pending" @click="cancel">取消上传</el-button>
  </div>
</template>
