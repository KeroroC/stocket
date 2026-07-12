<script setup lang="ts">
import { ref } from 'vue'
import { changePassword as apiChangePassword } from '../api/identity'
import type { ChangePasswordRequest } from '../api/identity'

const emit = defineEmits<{
  success: [data: ChangePasswordRequest]
  logout: []
}>()

const oldPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')

function validate(): string | null {
  if (!oldPassword.value) return '请输入旧密码'
  if (!newPassword.value) return '请输入新密码'
  if (newPassword.value.length < 8) return '新密码至少 8 个字符'
  if (newPassword.value === oldPassword.value) return '新密码不能与旧密码相同'
  if (newPassword.value !== confirmNewPassword.value) return '新密码不一致，请重新输入'
  return null
}

async function handleSubmit() {
  errorMessage.value = ''

  const validationError = validate()
  if (validationError) {
    errorMessage.value = validationError
    return
  }

  submitting.value = true
  try {
    await apiChangePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    })

    const dto: ChangePasswordRequest = {
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    }

    oldPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''

    emit('success', dto)
  } catch (err: unknown) {
    oldPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
    const problem = err as { detail?: string; code?: string }
    if (problem.code === 'INVALID_OLD_PASSWORD') {
      errorMessage.value = '旧密码错误'
    } else {
      errorMessage.value = problem.detail ?? '修改密码失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

function handleLogout() {
  oldPassword.value = ''
  newPassword.value = ''
  confirmNewPassword.value = ''
  emit('logout')
}
</script>

<template>
  <div class="auth-card">
    <h1>修改密码</h1>
    <p class="auth-subtitle">请修改初始密码后再继续使用</p>

    <form class="auth-form" @submit.prevent="handleSubmit">
      <div v-if="errorMessage" role="alert" class="auth-error">
        {{ errorMessage }}
      </div>

      <div class="form-field">
        <label for="oldPassword">旧密码</label>
        <input
          id="oldPassword"
          v-model="oldPassword"
          type="password"
          autocomplete="current-password"
        />
      </div>

      <div class="form-field">
        <label for="newPassword">新密码</label>
        <input
          id="newPassword"
          v-model="newPassword"
          type="password"
          placeholder="至少 8 个字符"
          autocomplete="new-password"
        />
      </div>

      <div class="form-field">
        <label for="confirmNewPassword">确认新密码</label>
        <input
          id="confirmNewPassword"
          v-model="confirmNewPassword"
          type="password"
          placeholder="再次输入新密码"
          autocomplete="new-password"
        />
      </div>

      <button type="submit" class="auth-submit" :disabled="submitting">
        {{ submitting ? '修改中...' : '修改密码' }}
      </button>

      <button type="button" class="auth-logout-btn" @click="handleLogout">
        退出登录
      </button>
    </form>
  </div>
</template>
