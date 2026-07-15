<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Camera, Refresh, SwitchButton } from '@element-plus/icons-vue'
import type { Scanner, ScanResult } from '../../scanner/Scanner'
import { parseScanPayload } from '../../scanner/scanPayload'

const props = defineProps<{ modelValue: boolean; scanner: Scanner }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  result: [value: ScanResult]
}>()

const video = ref<HTMLVideoElement>()
const error = ref('')
const manualValue = ref('')
const facingMode = ref<'environment' | 'user'>('environment')
const torchEnabled = ref(false)

function errorMessage(code?: string): string {
  if (code === 'CAMERA_PERMISSION_DENIED') return '摄像头权限被拒绝，请授权或使用手工输入。'
  if (code === 'CAMERA_NOT_FOUND') return '未找到可用摄像头，请使用手工输入。'
  if (code === 'CAMERA_INSECURE_CONTEXT') return '摄像头仅能在安全连接中使用。请通过 HTTPS 地址打开此页面，手机不能通过 http://局域网 IP 授权。'
  if (code === 'CAMERA_UNSUPPORTED') return '当前浏览器不支持摄像头访问，请使用最新版 Safari、Chrome 或手工输入。'
  return '摄像头暂不可用，请稍后重试。'
}

async function start() {
  if (!props.modelValue) return
  await nextTick()
  if (!video.value) return
  error.value = ''
  const availabilityError = props.scanner.availabilityError?.()
  if (availabilityError) {
    error.value = errorMessage(availabilityError)
    return
  }
  try {
    await props.scanner.start(video.value, (result) => emit('result', result))
  } catch (cause) {
    const code = (cause as { code?: string }).code
    error.value = errorMessage(code)
  }
}

async function stop() {
  await props.scanner.stop()
}

async function close() {
  await stop()
  emit('update:modelValue', false)
}

async function switchCamera() {
  facingMode.value = facingMode.value === 'environment' ? 'user' : 'environment'
  await props.scanner.setFacingMode?.(facingMode.value)
}

async function toggleTorch() {
  torchEnabled.value = !torchEnabled.value
  await props.scanner.toggleTorch?.(torchEnabled.value)
}

function useManualValue() {
  const result = parseScanPayload(manualValue.value)
  if (result) emit('result', result)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'hidden') void stop()
}

watch(video, (element) => {
  if (element && props.modelValue) void start()
})

watch(() => props.modelValue, (open) => {
  if (open) void start()
  else void stop()
})

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
})
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  void stop()
})
</script>

<template>
  <el-drawer :model-value="modelValue" title="扫描条码或位置码" direction="btt" size="min(85dvh, 42rem)" class="scanner-sheet" :teleported="false" :destroy-on-close="false" @close="close">
      <section aria-label="扫描条码或位置码">
        <video ref="video" muted playsinline aria-label="摄像头预览" />
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
        <div class="scanner-sheet__controls">
          <el-button :icon="Refresh" @click="switchCamera">切换摄像头</el-button>
          <el-button v-if="scanner.toggleTorch" :icon="SwitchButton" @click="toggleTorch">{{ torchEnabled ? '关闭手电筒' : '打开手电筒' }}</el-button>
        </div>
        <el-input v-model="manualValue" aria-label="手工输入条码或位置码" placeholder="手工输入条码或位置码"><template #prepend><el-icon><Camera /></el-icon></template><template #append><el-button @click="useManualValue">使用手工输入</el-button></template></el-input>
      </section>
  </el-drawer>
</template>

<style scoped>
.scanner-sheet section {
  display: grid;
  gap: var(--st-space-4);
}

.scanner-sheet video {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  max-height: min(42dvh, 22rem);
  border-radius: var(--st-radius-card);
  background: #16231f;
  object-fit: cover;
}

.scanner-sheet__controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--st-space-3);
}

.scanner-sheet__controls > :only-child {
  grid-column: 1 / -1;
}

</style>
