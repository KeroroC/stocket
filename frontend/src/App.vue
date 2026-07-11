<script setup lang="ts">
import { Box, CircleCheck, Warning } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { getSystemStatus } from './api/system'

const message = ref('正在连接后端…')
const connected = ref(false)

onMounted(async () => {
  try {
    const status = await getSystemStatus()
    connected.value = true
    message.value = `后端 ${status.version} 已连接`
  } catch {
    message.value = '后端暂不可用'
  }
})
</script>

<template>
  <main class="app-shell">
    <section class="foundation-card">
      <el-icon class="foundation-icon" :size="48" color="#2563eb">
        <Box />
      </el-icon>

      <h1>家庭资产</h1>
      <p class="foundation-description">工程基础已就绪</p>

      <el-tag
        class="foundation-status"
        :type="connected ? 'success' : 'warning'"
        effect="light"
        role="status"
        aria-live="polite"
        aria-atomic="true"
        round
      >
        <el-icon>
          <CircleCheck v-if="connected" />
          <Warning v-else />
        </el-icon>
        <span>{{ message }}</span>
      </el-tag>
    </section>
  </main>
</template>
