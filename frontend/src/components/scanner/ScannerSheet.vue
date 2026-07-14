<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Camera, Close, Refresh, SwitchButton } from '@element-plus/icons-vue'
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

async function start() {
  if (!props.modelValue) return
  await nextTick()
  if (!video.value) return
  error.value = ''
  try {
    await props.scanner.start(video.value, (result) => emit('result', result))
  } catch (cause) {
    const code = (cause as { code?: string }).code
    error.value = code === 'CAMERA_PERMISSION_DENIED'
      ? '摄像头权限被拒绝，请授权或使用手工输入。'
      : code === 'CAMERA_NOT_FOUND'
        ? '未找到可用摄像头，请使用手工输入。'
        : '摄像头暂不可用，请稍后重试。'
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

watch(() => props.modelValue, (open) => {
  if (open) void start()
  else void stop()
}, { immediate: true })

onMounted(() => document.addEventListener('visibilitychange', handleVisibilityChange))
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  void stop()
})
</script>

<template>
  <section v-if="modelValue" class="scanner-sheet" role="dialog" aria-label="扫描条码或位置码">
    <header>
      <h2><Camera aria-hidden="true" /> 扫描</h2>
      <button type="button" aria-label="关闭扫描器" @click="close"><Close /></button>
    </header>
    <video ref="video" muted playsinline aria-label="摄像头预览" />
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="scanner-sheet__controls">
      <button type="button" @click="switchCamera"><Refresh />切换摄像头</button>
      <button v-if="scanner.toggleTorch" type="button" @click="toggleTorch">
        <SwitchButton />{{ torchEnabled ? '关闭手电筒' : '打开手电筒' }}
      </button>
    </div>
    <label>
      <span>手工输入条码或位置码</span>
      <input v-model="manualValue" aria-label="手工输入条码或位置码" />
    </label>
    <button type="button" @click="useManualValue">使用手工输入</button>
  </section>
</template>
