<script setup lang="ts">
import { Box, CircleCheck } from '@element-plus/icons-vue'
</script>

<template>
  <main class="app-shell">
    <section class="foundation-card">
      <el-icon class="foundation-icon" :size="48" color="#2563eb">
        <Box />
      </el-icon>

      <h1>家庭资产</h1>
      <p class="foundation-description">工程基础已就绪</p>

      <el-tag class="foundation-status" type="success" effect="light" round>
        <el-icon>
          <CircleCheck />
        </el-icon>
        <span>Java 21 · Spring Boot Native · Vue 3</span>
      </el-tag>
    </section>
  </main>
</template>
