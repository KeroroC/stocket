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
  if (password.value.length < 8) return '密码至少 8 个字符'
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
  <div class="auth-card">
    <h1>初始化家庭</h1>
    <p class="auth-subtitle">首次使用需要创建家庭和管理员账户</p>

    <form class="auth-form" @submit.prevent="handleSubmit">
      <div v-if="errorMessage" role="alert" class="auth-error">
        {{ errorMessage }}
      </div>

      <div class="form-field">
        <label for="householdName">家庭名称</label>
        <input
          id="householdName"
          v-model="householdName"
          type="text"
          placeholder="例如：我的家庭"
          autocomplete="off"
        />
      </div>

      <div class="form-field">
        <label for="timezone">时区</label>
        <select id="timezone" v-model="timezone">
          <option v-for="tz in TIMEZONES" :key="tz" :value="tz">
            {{ tz }}
          </option>
        </select>
      </div>

      <div class="form-field">
        <label for="username">管理员用户名</label>
        <input
          id="username"
          v-model="username"
          type="text"
          placeholder="至少 3 个字符"
          autocomplete="username"
        />
      </div>

      <div class="form-field">
        <label for="displayName">显示名称</label>
        <input
          id="displayName"
          v-model="displayName"
          type="text"
          placeholder="例如：管理员"
          autocomplete="off"
        />
      </div>

      <div class="form-field">
        <label for="password">密码</label>
        <input
          id="password"
          v-model="password"
          type="password"
          placeholder="至少 8 个字符"
          autocomplete="new-password"
        />
      </div>

      <div class="form-field">
        <label for="confirmPassword">确认密码</label>
        <input
          id="confirmPassword"
          v-model="confirmPassword"
          type="password"
          placeholder="再次输入密码"
          autocomplete="new-password"
        />
      </div>

      <button type="submit" class="auth-submit" :disabled="submitting">
        {{ submitting ? '创建中...' : '创建家庭' }}
      </button>
    </form>
  </div>
</template>
