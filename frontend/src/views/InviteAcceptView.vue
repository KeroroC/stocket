<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { acceptInvite as apiAcceptInvite, getInviteStatus } from '../api/identity'
import type { AcceptInviteRequest, InviteStatusResponse } from '../api/identity'

const props = defineProps<{
  token: string
}>()

const emit = defineEmits<{
  success: [data: AcceptInviteRequest]
}>()

const status = ref<InviteStatusResponse | null>(null)
const loading = ref(true)
const loadError = ref('')

const username = ref('')
const displayName = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const errorMessage = ref('')

onMounted(async () => {
  try {
    status.value = await getInviteStatus(props.token)
  } catch {
    loadError.value = '邀请链接无效或已过期'
  } finally {
    loading.value = false
  }
})

function formatExpiry(iso: string): string {
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

function validate(): string | null {
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
    const trimmedUsername = username.value.trim()
    const trimmedDisplayName = displayName.value.trim()
    await apiAcceptInvite(props.token, {
      username: trimmedUsername,
      displayName: trimmedDisplayName,
      password: password.value,
    })

    password.value = ''
    confirmPassword.value = ''

    emit('success', {
      username: trimmedUsername,
      displayName: trimmedDisplayName,
      password: password.value,
    })
  } catch (err: unknown) {
    password.value = ''
    confirmPassword.value = ''
    const problem = err as { detail?: string; code?: string }
    if (problem.code === 'DUPLICATE_USERNAME') {
      errorMessage.value = '用户名已被使用，请换一个'
    } else {
      errorMessage.value = problem.detail ?? '接受邀请失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-card">
    <h1>接受邀请</h1>

    <div v-if="loading" class="auth-loading">
      <p>正在加载邀请信息...</p>
    </div>

    <div v-else-if="loadError" role="alert" class="auth-error">
      {{ loadError }}
    </div>

    <template v-else-if="status">
      <div class="invite-info">
        <p>
          <span class="invite-label">角色：</span>
          <span>{{ status.role }}</span>
        </p>
        <p>
          <span class="invite-label">到期时间：</span>
          <span>{{ formatExpiry(status.expiresAt) }}</span>
        </p>
      </div>

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
            placeholder="您的昵称"
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
          {{ submitting ? '加入中...' : '接受邀请' }}
        </button>
      </form>
    </template>
  </div>
</template>
