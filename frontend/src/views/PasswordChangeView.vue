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
  if (newPassword.value.length < 12) return '新密码至少 12 个字符'
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
    const currentOldPassword = oldPassword.value
    const currentNewPassword = newPassword.value
    await apiChangePassword({
      oldPassword: currentOldPassword,
      newPassword: currentNewPassword,
    })

    oldPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''

    emit('success', {
      oldPassword: currentOldPassword,
      newPassword: currentNewPassword,
    })
  } catch (err: unknown) {
    oldPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
    const problem = err as { detail?: string; code?: string }
    if (problem.code === 'INVALID_CREDENTIALS') {
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
  <el-card class="auth-card" shadow="always">
    <h1>修改密码</h1>
    <p class="auth-subtitle">请修改初始密码后再继续使用</p>

    <el-form class="auth-form" label-position="top" @submit.prevent="handleSubmit">
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
      <el-form-item label="旧密码"><el-input id="oldPassword" v-model="oldPassword" type="password" autocomplete="current-password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input id="newPassword" v-model="newPassword" type="password" placeholder="至少 12 个字符" autocomplete="new-password" show-password /></el-form-item>
      <el-form-item label="确认新密码"><el-input id="confirmNewPassword" v-model="confirmNewPassword" type="password" placeholder="再次输入新密码" autocomplete="new-password" show-password /></el-form-item>
      <el-button type="primary" size="large" :loading="submitting" :disabled="submitting" @click="handleSubmit">{{ submitting ? '修改中...' : '修改密码' }}</el-button>
      <el-button @click="handleLogout">退出登录</el-button>
    </el-form>
  </el-card>
</template>
