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
    await apiLogin({
      username: username.value.trim(),
      password: password.value,
    })

    const dto: LoginRequest = {
      username: username.value.trim(),
      password: password.value,
    }

    password.value = ''

    emit('success', dto)
  } catch {
    password.value = ''
    errorMessage.value = '用户名或密码错误'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-card">
    <h1>登录</h1>

    <form class="auth-form" @submit.prevent="handleSubmit">
      <div v-if="errorMessage" role="alert" class="auth-error">
        {{ errorMessage }}
      </div>

      <div class="form-field">
        <label for="username">用户名</label>
        <input
          id="username"
          v-model="username"
          type="text"
          autocomplete="username"
        />
      </div>

      <div class="form-field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
        />
      </div>

      <button type="submit" class="auth-submit" :disabled="submitting">
        {{ submitting ? '登录中...' : '登录' }}
      </button>
    </form>
  </div>
</template>
