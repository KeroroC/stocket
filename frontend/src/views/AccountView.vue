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
  if (newPassword.value.length < 12) {
    passwordError.value = '新密码至少 12 个字符'
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
    if (problem.code === 'INVALID_CREDENTIALS') {
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
    <el-tabs type="border-card">
      <el-tab-pane label="个人资料">
        <el-form class="auth-form" label-position="top" @submit.prevent="handleProfileSubmit">
          <el-alert v-if="profileError" :title="profileError" type="error" show-icon :closable="false" />
          <el-alert v-if="profileSuccess" title="资料已更新" type="success" show-icon :closable="false" />
          <el-form-item label="显示名称"><el-input id="displayName" v-model="displayName" autocomplete="name" /></el-form-item>
          <el-form-item label="邮箱（可选）"><el-input id="email" v-model="email" type="email" autocomplete="email" /></el-form-item>
          <el-button native-type="submit" type="primary" :loading="profileSaving">保存资料</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="修改密码">
        <el-form class="auth-form" label-position="top" @submit.prevent="handlePasswordSubmit">
          <el-alert v-if="passwordError" :title="passwordError" type="error" show-icon :closable="false" />
          <el-form-item label="当前密码"><el-input id="currentPassword" v-model="currentPassword" type="password" autocomplete="current-password" show-password /></el-form-item>
          <el-form-item label="新密码"><el-input id="newPassword" v-model="newPassword" type="password" placeholder="至少 12 个字符" autocomplete="new-password" show-password /></el-form-item>
          <el-form-item label="确认新密码"><el-input id="confirmNewPassword" v-model="confirmNewPassword" type="password" placeholder="再次输入新密码" autocomplete="new-password" show-password /></el-form-item>
          <el-button native-type="submit" type="primary" :loading="passwordSaving">修改密码</el-button>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="活跃会话">
        <el-alert v-if="sessionError" :title="sessionError" type="error" show-icon :closable="false" />
        <el-skeleton v-if="sessionsLoading" :rows="3" animated />
        <el-table v-else-if="sessions.length" :data="sessions" row-key="id">
          <el-table-column label="设备" min-width="220"><template #default="{ row }">{{ row.userAgent ?? '未知设备' }} <el-tag v-if="row.current" size="small" type="success">当前会话</el-tag></template></el-table-column>
          <el-table-column label="最后活跃" min-width="180"><template #default="{ row }">{{ new Date(row.lastSeenAt).toLocaleString() }}</template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="!row.current" link type="danger" @click="handleRevokeSession(row.id)">撤销</el-button></template></el-table-column>
        </el-table>
        <el-empty v-else description="暂无活跃会话" />
        <el-button v-if="sessions.length > 1" type="danger" plain @click="handleRevokeOtherSessions">撤销其他全部会话</el-button>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
