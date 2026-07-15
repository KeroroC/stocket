<script setup lang="ts">
import { ref } from 'vue'
import { initialize } from '../api/identity'
import type { InitializeRequest } from '../api/identity'

const emit = defineEmits<{
  success: [data: InitializeRequest]
}>()

const householdName = ref('')
const timezone = ref('Asia/Shanghai')
const username = ref('')
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')

const TIMEZONES = [
  'Asia/Shanghai',
  'Asia/Tokyo',
  'Asia/Seoul',
  'Asia/Singapore',
  'Asia/Hong_Kong',
  'Asia/Taipei',
  'Asia/Bangkok',
  'Asia/Kolkata',
  'Asia/Dubai',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Moscow',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Sao_Paulo',
  'Australia/Sydney',
  'Pacific/Auckland',
]

function validate(): string | null {
  if (!householdName.value.trim()) return '请输入家庭名称'
  if (!username.value.trim()) return '请输入用户名'
  if (username.value.trim().length < 3) return '用户名至少 3 个字符'
  if (!displayName.value.trim()) return '请输入显示名称'
  if (!password.value) return '请输入密码'
  if (password.value.length < 12) return '密码至少 12 个字符'
  if (password.value !== confirmPassword.value) return '密码不一致，请重新输入'
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
    await initialize({
      householdName: householdName.value.trim(),
      timezone: timezone.value,
      username: username.value.trim(),
      displayName: displayName.value.trim(),
      password: password.value,
    })

    password.value = ''
    confirmPassword.value = ''

    emit('success', {
      householdName: householdName.value.trim(),
      timezone: timezone.value,
      username: username.value.trim(),
      displayName: displayName.value.trim(),
      password: '',
    })
  } catch (err: unknown) {
    const problem = err as { detail?: string; code?: string }
    errorMessage.value = problem.detail ?? '创建失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-card class="auth-card" shadow="always">
    <h1>初始化家庭</h1>
    <p class="auth-subtitle">首次使用需要创建家庭和管理员账户</p>

    <el-form class="auth-form" label-position="top" @submit.prevent="handleSubmit">
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
      <el-form-item label="家庭名称"><el-input id="householdName" v-model="householdName" placeholder="例如：我的家庭" autocomplete="off" /></el-form-item>
      <el-form-item label="时区"><el-select id="timezone" v-model="timezone" filterable><el-option v-for="tz in TIMEZONES" :key="tz" :label="tz" :value="tz" /></el-select></el-form-item>
      <el-form-item label="管理员用户名"><el-input id="username" v-model="username" placeholder="至少 3 个字符" autocomplete="username" /></el-form-item>
      <el-form-item label="显示名称"><el-input id="displayName" v-model="displayName" placeholder="例如：管理员" autocomplete="off" /></el-form-item>
      <el-form-item label="密码"><el-input id="password" v-model="password" type="password" placeholder="至少 12 个字符" autocomplete="new-password" show-password /></el-form-item>
      <el-form-item label="确认密码"><el-input id="confirmPassword" v-model="confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" show-password /></el-form-item>
      <el-button type="primary" size="large" :loading="submitting" :disabled="submitting" @click="handleSubmit">{{ submitting ? '创建中...' : '创建家庭' }}</el-button>
    </el-form>
  </el-card>
</template>
