<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { InviteListItem } from '../api/identity'
import {
  getInvites as apiGetInvites,
  createInvite as apiCreateInvite,
  revokeInvite as apiRevokeInvite,
} from '../api/identity'

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
}>()

// Invites state
const invites = ref<InviteListItem[]>([])
const loading = ref(false)
const error = ref('')

// Create invite dialog
const showCreateDialog = ref(false)
const newRole = ref('MEMBER')
const newExpiresInHours = ref(24)
const createSubmitting = ref(false)
const createError = ref('')

// Result dialog (invite link)
const showResultDialog = ref(false)
const resultInviteLink = ref('')

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

async function loadInvites() {
  loading.value = true
  error.value = ''
  try {
    invites.value = await apiGetInvites()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) error.value = msg
  } finally {
    loading.value = false
  }
}

onMounted(loadInvites)

function openCreateDialog() {
  newRole.value = 'MEMBER'
  newExpiresInHours.value = 24
  createError.value = ''
  showCreateDialog.value = true
}

async function handleCreateInvite() {
  createError.value = ''
  createSubmitting.value = true
  try {
    const result = await apiCreateInvite({
      role: newRole.value,
      expiresInHours: newExpiresInHours.value,
    })
    showCreateDialog.value = false
    resultInviteLink.value = result.inviteLink
    showResultDialog.value = true
    await loadInvites()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) createError.value = msg
  } finally {
    createSubmitting.value = false
  }
}

function closeResultDialog() {
  resultInviteLink.value = ''
  showResultDialog.value = false
}

async function handleRevokeInvite(inviteId: string) {
  error.value = ''
  try {
    await apiRevokeInvite(inviteId)
    await loadInvites()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) error.value = msg
  }
}

function formatRole(role: string): string {
  switch (role) {
    case 'ADMIN': return '管理员'
    case 'MEMBER': return '成员'
    case 'VIEWER': return '只读者'
    default: return role
  }
}

function formatStatus(status: string): string {
  switch (status) {
    case 'PENDING': return '待使用'
    case 'ACCEPTED': return '已接受'
    case 'EXPIRED': return '已过期'
    case 'REVOKED': return '已撤销'
    default: return status
  }
}

function formatStatusType(status: string): string {
  switch (status) {
    case 'PENDING': return 'success'
    case 'ACCEPTED': return ''
    case 'EXPIRED': return 'warning'
    case 'REVOKED': return 'info'
    default: return ''
  }
}
</script>

<template>
  <div class="admin-invites-view">
    <div class="section-header">
      <h2 class="section-title">邀请管理</h2>
      <button class="auth-submit" style="width: auto; height: 40px; font-size: 0.875rem;" @click="openCreateDialog">
        创建邀请
      </button>
    </div>

    <div v-if="error" role="alert" class="auth-error">
      {{ error }}
    </div>

    <div v-if="loading" class="auth-loading">加载中...</div>

    <ul v-if="invites.length > 0" class="invite-list">
      <li v-for="invite in invites" :key="invite.id" class="invite-item">
        <div class="invite-info">
          <el-tag :type="formatStatusType(invite.status) as any" size="small">
            {{ formatStatus(invite.status) }}
          </el-tag>
          <span class="invite-role">{{ formatRole(invite.role) }}</span>
          <span class="invite-expires">
            有效期至：{{ new Date(invite.expiresAt).toLocaleString() }}
          </span>
        </div>
        <button
          v-if="invite.status === 'PENDING'"
          class="member-action-btn"
          @click="handleRevokeInvite(invite.id)"
        >
          撤销
        </button>
      </li>
    </ul>

    <p v-else-if="!loading" class="auth-loading">暂无邀请</p>

    <!-- Create invite dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建邀请"
      :close-on-click-modal="false"
      width="400px"
    >
      <form class="auth-form" @submit.prevent="handleCreateInvite">
        <div v-if="createError" role="alert" class="auth-error">
          {{ createError }}
        </div>

        <div class="form-field">
          <label for="inviteRole">角色</label>
          <select id="inviteRole" v-model="newRole">
            <option value="ADMIN">管理员</option>
            <option value="MEMBER">成员</option>
            <option value="VIEWER">只读者</option>
          </select>
        </div>

        <div class="form-field">
          <label for="inviteExpiry">有效期（小时）</label>
          <input
            id="inviteExpiry"
            v-model.number="newExpiresInHours"
            type="number"
            min="1"
            max="720"
          />
        </div>
      </form>
      <template #footer>
        <button class="auth-logout-btn" style="width: auto; height: 36px;" @click="showCreateDialog = false">取消</button>
        <button class="auth-submit" style="width: auto; height: 36px;" :disabled="createSubmitting" @click="handleCreateInvite">
          {{ createSubmitting ? '创建中...' : '确认创建' }}
        </button>
      </template>
    </el-dialog>

    <!-- Result dialog (invite link) -->
    <el-dialog
      v-model="showResultDialog"
      title="邀请创建成功"
      :close-on-click-modal="false"
      width="400px"
      @close="closeResultDialog"
    >
      <div class="result-content">
        <p>邀请链接（仅显示一次，请妥善保存）：</p>
        <div class="result-secret">{{ resultInviteLink }}</div>
      </div>
      <template #footer>
        <button class="auth-submit" style="width: auto; height: 36px;" @click="closeResultDialog">
          确定
        </button>
      </template>
    </el-dialog>
  </div>
</template>
