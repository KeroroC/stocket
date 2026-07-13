<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { MemberInfo } from '../api/identity'
import {
  getMembers as apiGetMembers,
  createMember as apiCreateMember,
  updateMemberRole as apiUpdateMemberRole,
  resetMemberPassword as apiResetMemberPassword,
  enableMember as apiEnableMember,
  disableMember as apiDisableMember,
} from '../api/identity'

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
}>()

// Members state
const members = ref<MemberInfo[]>([])
const loading = ref(false)
const error = ref('')

// Create member dialog
const showCreateDialog = ref(false)
const newUsername = ref('')
const newDisplayName = ref('')
const newRole = ref('MEMBER')
const createSubmitting = ref(false)
const createError = ref('')

// Result dialog (temp password)
const showResultDialog = ref(false)
const resultTempPassword = ref('')

// Edit role dialog
const showEditRoleDialog = ref(false)
const editingMember = ref<MemberInfo | null>(null)
const editRole = ref('')
const editSubmitting = ref(false)
const editError = ref('')

// Reset password result
const showResetResultDialog = ref(false)
const resetTempPassword = ref('')

// Copy state
const copiedCreate = ref(false)
const copiedReset = ref(false)

async function copyToClipboard(text: string, target: 'create' | 'reset') {
  try {
    await navigator.clipboard.writeText(text)
    if (target === 'create') {
      copiedCreate.value = true
      setTimeout(() => { copiedCreate.value = false }, 2000)
    } else {
      copiedReset.value = true
      setTimeout(() => { copiedReset.value = false }, 2000)
    }
  } catch {
    // Fallback: select the text for manual copy
  }
}

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
  if (problem.code === 'LAST_ADMIN_REQUIRED') {
    return '不能移除或降级最后一位管理员'
  }
  return problem.detail ?? '操作失败，请稍后重试'
}

async function loadMembers() {
  loading.value = true
  error.value = ''
  try {
    members.value = await apiGetMembers()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) error.value = msg
  } finally {
    loading.value = false
  }
}

onMounted(loadMembers)

function openCreateDialog() {
  newUsername.value = ''
  newDisplayName.value = ''
  newRole.value = 'MEMBER'
  createError.value = ''
  showCreateDialog.value = true
}

async function handleCreateMember() {
  createError.value = ''
  if (!newUsername.value.trim()) {
    createError.value = '请输入用户名'
    return
  }
  if (!newDisplayName.value.trim()) {
    createError.value = '请输入显示名称'
    return
  }

  createSubmitting.value = true
  try {
    const result = await apiCreateMember({
      username: newUsername.value.trim(),
      displayName: newDisplayName.value.trim(),
      role: newRole.value,
    })
    showCreateDialog.value = false
    resultTempPassword.value = result.temporaryPassword
    showResultDialog.value = true
    await loadMembers()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) createError.value = msg
  } finally {
    createSubmitting.value = false
  }
}

function closeResultDialog() {
  resultTempPassword.value = ''
  showResultDialog.value = false
}

function openEditRoleDialog(member: MemberInfo) {
  editingMember.value = member
  editRole.value = member.role
  editError.value = ''
  showEditRoleDialog.value = true
}

async function handleEditRole() {
  if (!editingMember.value) return
  editError.value = ''
  editSubmitting.value = true
  try {
    await apiUpdateMemberRole(editingMember.value.id, editRole.value)
    showEditRoleDialog.value = false
    await loadMembers()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) editError.value = msg
  } finally {
    editSubmitting.value = false
  }
}

async function handleToggleEnabled(member: MemberInfo) {
  error.value = ''
  try {
    if (member.enabled) {
      await apiDisableMember(member.id)
    } else {
      await apiEnableMember(member.id)
    }
    await loadMembers()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) error.value = msg
  }
}

async function handleResetPassword(member: MemberInfo) {
  error.value = ''
  try {
    const result = await apiResetMemberPassword(member.id)
    resetTempPassword.value = result.temporaryPassword
    showResetResultDialog.value = true
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) error.value = msg
  }
}

function closeResetResultDialog() {
  resetTempPassword.value = ''
  showResetResultDialog.value = false
}

function formatRole(role: string): string {
  switch (role) {
    case 'ADMIN': return '管理员'
    case 'MEMBER': return '成员'
    case 'VIEWER': return '只读者'
    default: return role
  }
}
</script>

<template>
  <div class="admin-members-view">
    <div class="section-header">
      <h2 class="section-title">成员管理</h2>
      <button class="auth-submit" style="width: auto; height: 40px; font-size: 0.875rem;" @click="openCreateDialog">
        创建成员
      </button>
    </div>

    <div v-if="error" role="alert" class="auth-error">
      {{ error }}
    </div>

    <div v-if="loading" class="auth-loading">加载中...</div>

    <ul v-if="members.length > 0" class="member-list">
      <li v-for="member in members" :key="member.id" class="member-item">
        <div class="member-info">
          <span class="member-name">{{ member.displayName }}</span>
          <span class="member-username">@{{ member.username }}</span>
          <el-tag :type="member.role === 'ADMIN' ? 'success' : member.role === 'MEMBER' ? 'primary' : 'info'" size="small">
            {{ formatRole(member.role) }}
          </el-tag>
          <el-tag v-if="!member.enabled" type="danger" size="small">已禁用</el-tag>
        </div>
        <div class="member-actions">
          <button class="member-action-btn" @click="openEditRoleDialog(member)">修改角色</button>
          <button class="member-action-btn" @click="handleToggleEnabled(member)">
            {{ member.enabled ? '禁用' : '启用' }}
          </button>
          <button class="member-action-btn" @click="handleResetPassword(member)">重置密码</button>
        </div>
      </li>
    </ul>

    <p v-else-if="!loading" class="auth-loading">暂无成员</p>

    <!-- Create member dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建成员"
      :close-on-click-modal="false"
      width="400px"
    >
      <form class="auth-form" @submit.prevent="handleCreateMember">
        <div v-if="createError" role="alert" class="auth-error">
          {{ createError }}
        </div>

        <div class="form-field">
          <label for="newUsername">用户名</label>
          <input
            id="newUsername"
            v-model="newUsername"
            type="text"
            autocomplete="off"
          />
        </div>

        <div class="form-field">
          <label for="newDisplayName">显示名称</label>
          <input
            id="newDisplayName"
            v-model="newDisplayName"
            type="text"
            autocomplete="off"
          />
        </div>

        <div class="form-field">
          <label for="newRole">角色</label>
          <select id="newRole" v-model="newRole">
            <option value="ADMIN">管理员</option>
            <option value="MEMBER">成员</option>
            <option value="VIEWER">只读者</option>
          </select>
        </div>
      </form>
      <template #footer>
        <button class="auth-logout-btn" style="width: auto; height: 36px;" @click="showCreateDialog = false">取消</button>
        <button class="auth-submit" style="width: auto; height: 36px;" :disabled="createSubmitting" @click="handleCreateMember">
          {{ createSubmitting ? '创建中...' : '确认创建' }}
        </button>
      </template>
    </el-dialog>

    <!-- Result dialog (temporary password) -->
    <el-dialog
      v-model="showResultDialog"
      title="成员创建成功"
      :close-on-click-modal="false"
      width="400px"
      @close="closeResultDialog"
    >
      <div class="result-content">
        <p>临时密码（仅显示一次，请妥善保存）：</p>
        <div class="result-secret">{{ resultTempPassword }}</div>
        <button
          class="result-copy-btn"
          :disabled="copiedCreate"
          @click="copyToClipboard(resultTempPassword, 'create')"
        >
          {{ copiedCreate ? '已复制' : '复制密码' }}
        </button>
      </div>
      <template #footer>
        <button class="auth-submit" style="width: auto; height: 36px;" @click="closeResultDialog">
          确定
        </button>
      </template>
    </el-dialog>

    <!-- Edit role dialog -->
    <el-dialog
      v-model="showEditRoleDialog"
      title="修改角色"
      :close-on-click-modal="false"
      width="400px"
    >
      <form class="auth-form" @submit.prevent="handleEditRole">
        <div v-if="editError" role="alert" class="auth-error">
          {{ editError }}
        </div>

        <div class="form-field">
          <label for="editRole">新角色</label>
          <select id="editRole" v-model="editRole">
            <option value="ADMIN">管理员</option>
            <option value="MEMBER">成员</option>
            <option value="VIEWER">只读者</option>
          </select>
        </div>
      </form>
      <template #footer>
        <button class="auth-logout-btn" style="width: auto; height: 36px;" @click="showEditRoleDialog = false">取消</button>
        <button class="auth-submit" style="width: auto; height: 36px;" :disabled="editSubmitting" @click="handleEditRole">
          {{ editSubmitting ? '保存中...' : '确认' }}
        </button>
      </template>
    </el-dialog>

    <!-- Reset password result dialog -->
    <el-dialog
      v-model="showResetResultDialog"
      title="密码重置成功"
      :close-on-click-modal="false"
      width="400px"
      @close="closeResetResultDialog"
    >
      <div class="result-content">
        <p>临时密码（仅显示一次，请妥善保存）：</p>
        <div class="result-secret">{{ resetTempPassword }}</div>
        <button
          class="result-copy-btn"
          :disabled="copiedReset"
          @click="copyToClipboard(resetTempPassword, 'reset')"
        >
          {{ copiedReset ? '已复制' : '复制密码' }}
        </button>
      </div>
      <template #footer>
        <button class="auth-submit" style="width: auto; height: 36px;" @click="closeResetResultDialog">
          确定
        </button>
      </template>
    </el-dialog>
  </div>
</template>
