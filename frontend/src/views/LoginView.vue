<script setup lang="ts">
import { ref } from 'vue'
import { login as apiLogin } from '../api/identity'
import type { LoginRequest } from '../api/identity'

const emit = defineEmits<{
  success: [data: LoginRequest]
}>()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function handleSubmit() {
  errorMessage.value = ''

  if (!username.value.trim()) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (!password.value) {
    errorMessage.value = '请输入密码'
    return
  }

  submitting.value = true
  try {
    const trimmedUsername = username.value.trim()
    const currentPassword = password.value
    await apiLogin({
      username: trimmedUsername,
      password: currentPassword,
    })

    password.value = ''

    emit('success', {
      username: trimmedUsername,
      password: currentPassword,
    })
  } catch {
    password.value = ''
    errorMessage.value = '用户名或密码错误'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card class="auth-card" shadow="always">
    <h1>登录</h1>
    <el-form class="auth-form" label-position="top" @submit.prevent="handleSubmit">
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
      <el-form-item label="用户名">
        <el-input id="username" v-model="username" autocomplete="username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input id="password" v-model="password" type="password" autocomplete="current-password" show-password />
      </el-form-item>
      <el-button type="primary" size="large" :loading="submitting" :disabled="submitting" @click="handleSubmit">{{ submitting ? '登录中...' : '登录' }}</el-button>
    </el-form>
  </el-card>
</template>
