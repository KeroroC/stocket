<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { CurrentAccount } from '../auth/AuthState'
import type { SessionInfo } from '../api/identity'
import {
  getCurrentAccount,
  updateProfile as apiUpdateProfile,
  changePassword as apiChangePassword,
  getSessions as apiGetSessions,
  revokeSession as apiRevokeSession,
  revokeOtherSessions as apiRevokeOtherSessions,
} from '../api/identity'

const props = defineProps<{
  account: CurrentAccount
}>()

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
  profileUpdated: []
}>()

// Profile state
const displayName = ref('')
const email = ref('')
const profileSaving = ref(false)
const profileError = ref('')
const profileSuccess = ref(false)

// Password state
const currentPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const passwordSaving = ref(false)
const passwordError = ref('')

// Session state
const sessions = ref<SessionInfo[]>([])
const sessionsLoading = ref(false)
const sessionError = ref('')

function handleApiError(err: unknown): string {
  const problem = err as { status?: number; code?: string; detail?: string }
  if (problem.status === 401) {
    emit('logout')
    return ''
  }
  if (problem.code === 'PASSWORD_CHANGE_REQUIRED') {
    emit('forcePasswordChange')
    return ''
  }
  return problem.detail ?? '操作失败，请稍后重试'
}

async function loadProfile() {
  try {
    const account = await getCurrentAccount()
    displayName.value = account.displayName
    email.value = account.email ?? ''
  } catch (err: unknown) {
    handleApiError(err)
  }
}

async function loadSessions() {
  sessionsLoading.value = true
  sessionError.value = ''
  try {
    sessions.value = await apiGetSessions()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) sessionError.value = msg
  } finally {
    sessionsLoading.value = false
  }
}

onMounted(() => {
  loadProfile()
  loadSessions()
})

async function handleProfileSubmit() {
  profileError.value = ''
  profileSuccess.value = false

  if (!displayName.value.trim()) {
    profileError.value = '请输入显示名称'
    return
  }

  profileSaving.value = true
  try {
    await apiUpdateProfile({
      displayName: displayName.value.trim(),
      email: email.value.trim() || null,
    })
    profileSuccess.value = true
    emit('profileUpdated')
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) profileError.value = msg
  } finally {
    profileSaving.value = false
  }
}

async function handlePasswordSubmit() {
  passwordError.value = ''

  if (!currentPassword.value) {
    passwordError.value = '请输入当前密码'
    return
  }
  if (!newPassword.value) {
    passwordError.value = '请输入新密码'
    return
  }
  if (newPassword.value.length < 8) {
    passwordError.value = '新密码至少 8 个字符'
    return
  }
  if (newPassword.value !== confirmNewPassword.value) {
    passwordError.value = '新密码不一致，请重新输入'
    return
  }

  passwordSaving.value = true
  try {
    await apiChangePassword({
      oldPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
  } catch (err: unknown) {
    currentPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
    const problem = err as { code?: string; detail?: string }
    if (problem.code === 'INVALID_OLD_PASSWORD') {
      passwordError.value = '旧密码错误'
    } else {
      passwordError.value = handleApiError(err) || '修改密码失败，请稍后重试'
    }
  } finally {
    passwordSaving.value = false
  }
}

async function handleRevokeSession(sessionId: string) {
  sessionError.value = ''
  try {
    await apiRevokeSession(sessionId)
    await loadSessions()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) sessionError.value = msg
  }
}

async function handleRevokeOtherSessions() {
  sessionError.value = ''
  try {
    await apiRevokeOtherSessions()
    await loadSessions()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) sessionError.value = msg
  }
}
</script>

<template>
  <div class="account-view">
    <h2 class="section-title">我的账户</h2>

    <!-- Profile section -->
    <section class="account-section">
      <h3>个人资料</h3>
      <form class="auth-form" @submit.prevent="handleProfileSubmit">
        <div v-if="profileError" role="alert" class="auth-error">
          {{ profileError }}
        </div>
        <div v-if="profileSuccess" class="auth-success">
          资料已更新
        </div>

        <div class="form-field">
          <label for="displayName">显示名称</label>
          <input
            id="displayName"
            v-model="displayName"
            type="text"
            autocomplete="name"
          />
        </div>

        <div class="form-field">
          <label for="email">邮箱（可选）</label>
          <input
            id="email"
            v-model="email"
            type="email"
            autocomplete="email"
          />
        </div>

        <button type="submit" class="auth-submit" :disabled="profileSaving">
          {{ profileSaving ? '保存中...' : '保存资料' }}
        </button>
      </form>
    </section>

    <!-- Password section -->
    <section class="account-section">
      <h3>修改密码</h3>
      <form class="auth-form" @submit.prevent="handlePasswordSubmit">
        <div v-if="passwordError" role="alert" class="auth-error">
          {{ passwordError }}
        </div>

        <div class="form-field">
          <label for="currentPassword">当前密码</label>
          <input
            id="currentPassword"
            v-model="currentPassword"
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

        <button type="submit" class="auth-submit" :disabled="passwordSaving">
          {{ passwordSaving ? '修改中...' : '修改密码' }}
        </button>
      </form>
    </section>

    <!-- Sessions section -->
    <section class="account-section">
      <h3>活跃会话</h3>
      <div v-if="sessionError" role="alert" class="auth-error">
        {{ sessionError }}
      </div>
      <div v-if="sessionsLoading" class="auth-loading">加载中...</div>

      <ul v-if="sessions.length > 0" class="session-list">
        <li
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { current: session.current }]"
        >
          <div class="session-info">
            <span class="session-agent">{{ session.userAgent ?? '未知设备' }}</span>
            <span v-if="session.current" class="session-current-badge">当前会话</span>
            <span class="session-time">
              最后活跃：{{ new Date(session.lastSeenAt).toLocaleString() }}
            </span>
          </div>
          <button
            v-if="!session.current"
            class="session-revoke-btn"
            @click="handleRevokeSession(session.id)"
          >
            撤销
          </button>
        </li>
      </ul>

      <p v-else-if="!sessionsLoading" class="auth-loading">暂无活跃会话</p>

      <button
        v-if="sessions.length > 1"
        class="auth-logout-btn"
        @click="handleRevokeOtherSessions"
      >
        撤销其他全部会话
      </button>
    </section>
  </div>
</template>
