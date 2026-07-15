<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { MemberInfo } from '../api/identity'
import StPageHeader from '../components/StPageHeader.vue'
import { useDesktopLayout } from '../composables/useDesktopLayout'
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

const { isDesktop } = useDesktopLayout()

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
  <section class="st-page admin-members-view">
    <StPageHeader title="成员管理" description="管理家庭成员角色与访问状态">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">创建成员</el-button>
      </template>
    </StPageHeader>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-skeleton v-if="loading" :rows="5" animated />

    <el-table v-if="isDesktop && members.length > 0" :data="members" row-key="id">
      <el-table-column prop="displayName" label="显示名" min-width="130" />
      <el-table-column label="用户名" min-width="130"><template #default="{ row }">@{{ row.username }}</template></el-table-column>
      <el-table-column label="角色" width="110"><template #default="{ row }"><el-tag :type="row.role === 'ADMIN' ? 'success' : row.role === 'MEMBER' ? 'primary' : 'info'">{{ formatRole(row.role) }}</el-tag></template></el-table-column>
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'danger'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" min-width="240" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openEditRoleDialog(row as MemberInfo)">修改角色</el-button><el-button link :type="row.enabled ? 'danger' : 'success'" @click="handleToggleEnabled(row as MemberInfo)">{{ row.enabled ? '禁用' : '启用' }}</el-button><el-button link @click="handleResetPassword(row as MemberInfo)">重置密码</el-button></template></el-table-column>
    </el-table>

    <ul v-if="!isDesktop && members.length > 0" class="member-list">
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
          <el-button size="small" @click="openEditRoleDialog(member)">修改角色</el-button>
          <el-button size="small" @click="handleToggleEnabled(member)">
            {{ member.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button size="small" @click="handleResetPassword(member)">重置密码</el-button>
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
      <el-form class="auth-form" label-position="top" @submit.prevent="handleCreateMember">
        <el-alert v-if="createError" :title="createError" type="error" show-icon :closable="false" />
        <el-form-item label="用户名"><el-input id="newUsername" v-model="newUsername" autocomplete="off" /></el-form-item>
        <el-form-item label="显示名称"><el-input id="newDisplayName" v-model="newDisplayName" autocomplete="off" /></el-form-item>
        <el-form-item label="角色"><el-select id="newRole" v-model="newRole"><el-option label="管理员" value="ADMIN" /><el-option label="成员" value="MEMBER" /><el-option label="只读者" value="VIEWER" /></el-select></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button><el-button type="primary" :loading="createSubmitting" @click="handleCreateMember">确认创建</el-button>
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
        <el-button :disabled="copiedCreate" @click="copyToClipboard(resultTempPassword, 'create')">
          {{ copiedCreate ? '已复制' : '复制密码' }}
        </el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="closeResultDialog">确定</el-button>
      </template>
    </el-dialog>

    <!-- Edit role dialog -->
    <el-dialog
      v-model="showEditRoleDialog"
      title="修改角色"
      :close-on-click-modal="false"
      width="400px"
    >
      <el-form class="auth-form" label-position="top" @submit.prevent="handleEditRole"><el-alert v-if="editError" :title="editError" type="error" show-icon :closable="false" /><el-form-item label="新角色"><el-select id="editRole" v-model="editRole"><el-option label="管理员" value="ADMIN" /><el-option label="成员" value="MEMBER" /><el-option label="只读者" value="VIEWER" /></el-select></el-form-item></el-form>
      <template #footer>
        <el-button @click="showEditRoleDialog = false">取消</el-button><el-button type="primary" :loading="editSubmitting" @click="handleEditRole">确认</el-button>
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
        <el-button :disabled="copiedReset" @click="copyToClipboard(resetTempPassword, 'reset')">
          {{ copiedReset ? '已复制' : '复制密码' }}
        </el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="closeResetResultDialog">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>
