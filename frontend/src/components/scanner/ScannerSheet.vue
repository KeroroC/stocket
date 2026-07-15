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
  <Teleport to="body">
    <div v-if="modelValue" class="scanner-overlay">
      <section class="scanner-sheet" role="dialog" aria-modal="true" aria-label="扫描条码或位置码">
        <header>
          <h2><Camera aria-hidden="true" /> 扫描</h2>
          <button class="scanner-sheet__close" type="button" aria-label="关闭扫描器" @click="close"><Close /></button>
        </header>
        <video ref="video" muted playsinline aria-label="摄像头预览" />
        <p v-if="error" class="scanner-sheet__error" role="alert">{{ error }}</p>
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
        <button class="scanner-sheet__submit" type="button" @click="useManualValue">使用手工输入</button>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.scanner-overlay {
  position: fixed;
  z-index: 100;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-top: env(safe-area-inset-top);
  background: rgb(32 51 44 / 42%);
  backdrop-filter: blur(2px);
}

.scanner-sheet {
  display: grid;
  width: min(100%, 36rem);
  max-height: calc(100dvh - env(safe-area-inset-top));
  overflow-y: auto;
  gap: var(--st-space-4);
  padding: var(--st-space-4);
  padding-bottom: calc(var(--st-space-4) + env(safe-area-inset-bottom));
  border: 1px solid var(--st-color-border);
  border-bottom: 0;
  border-radius: var(--st-radius-feature) var(--st-radius-feature) 0 0;
  background: var(--st-color-surface);
  box-shadow: 0 -16px 48px rgb(32 51 44 / 18%);
}

.scanner-sheet header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--st-space-3);
}

.scanner-sheet h2 {
  display: flex;
  align-items: center;
  gap: var(--st-space-2);
  margin: 0;
  color: var(--st-color-text);
  font-size: 1.25rem;
}

.scanner-sheet h2 svg,
.scanner-sheet button svg {
  width: 1.25rem;
  height: 1.25rem;
  flex: 0 0 auto;
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

.scanner-sheet button,
.scanner-sheet input {
  min-height: var(--st-control-min-size);
  border: 1px solid var(--st-color-border);
  border-radius: var(--st-radius-control);
  font: inherit;
}

.scanner-sheet button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--st-space-2);
  padding: 0 var(--st-space-3);
  background: var(--st-color-surface);
  color: var(--st-color-text);
  font-weight: 650;
  cursor: pointer;
}

.scanner-sheet .scanner-sheet__close {
  width: var(--st-control-min-size);
  padding: 0;
}

.scanner-sheet__controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--st-space-3);
}

.scanner-sheet__controls > :only-child {
  grid-column: 1 / -1;
}

.scanner-sheet label {
  display: grid;
  gap: var(--st-space-2);
  color: var(--st-color-text);
  font-size: 0.875rem;
  font-weight: 650;
}

.scanner-sheet input {
  width: 100%;
  min-width: 0;
  padding: 0 var(--st-space-3);
  outline: 0;
  background: var(--st-color-bg);
  color: var(--st-color-text);
}

.scanner-sheet input:focus {
  border-color: var(--st-color-primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--st-color-primary) 14%, transparent);
}

.scanner-sheet .scanner-sheet__submit {
  border-color: var(--st-color-primary);
  background: var(--st-color-primary);
  color: white;
}

.scanner-sheet__error {
  margin: 0;
  padding: var(--st-space-3);
  border-radius: var(--st-radius-control);
  background: var(--st-color-danger-soft);
  color: var(--st-color-danger);
  line-height: 1.5;
}

@media (min-width: 768px) {
  .scanner-overlay {
    align-items: center;
    padding: var(--st-space-6);
  }

  .scanner-sheet {
    max-height: calc(100dvh - 2 * var(--st-space-6));
    padding: var(--st-space-6);
    border-bottom: 1px solid var(--st-color-border);
    border-radius: var(--st-radius-feature);
  }
}
</style>
