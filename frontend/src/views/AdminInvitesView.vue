<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { InviteListItem } from '../api/identity'
import StPageHeader from '../components/StPageHeader.vue'
import { useDesktopLayout } from '../composables/useDesktopLayout'
import {
  getInvites as apiGetInvites,
  createInvite as apiCreateInvite,
  revokeInvite as apiRevokeInvite,
  extendInvite as apiExtendInvite,
} from '../api/identity'

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
}>()

const { isDesktop } = useDesktopLayout()

// Invites state
const invites = ref<InviteListItem[]>([])
const loading = ref(false)
const error = ref('')

// Create invite dialog
const showCreateDialog = ref(false)
const newRole = ref('MEMBER')
const newExpiresInHours = ref(24)
const newMaxUses = ref(1)
const createSubmitting = ref(false)
const createError = ref('')

// Result dialog (invite link)
const showResultDialog = ref(false)
const resultInviteLink = ref('')

// Copy state
const copied = ref(false)

// Extend invite dialog
const showExtendDialog = ref(false)
const extendInviteId = ref('')
const newExpiryDate = ref('')
const extendSubmitting = ref(false)
const extendError = ref('')

async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
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
  newMaxUses.value = 1
  createError.value = ''
  showCreateDialog.value = true
}

async function handleCreateInvite() {
  createError.value = ''
  createSubmitting.value = true
  try {
    const expiresAt = new Date(
      Date.now() + newExpiresInHours.value * 60 * 60 * 1000,
    ).toISOString()
    const result = await apiCreateInvite({
      role: newRole.value,
      expiresAt,
      maxUses: newMaxUses.value,
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

function openExtendDialog(inviteId: string, currentExpiry: string) {
  extendInviteId.value = inviteId
  extendError.value = ''
  // Default to 7 days from now
  const defaultDate = new Date()
  defaultDate.setDate(defaultDate.getDate() + 7)
  newExpiryDate.value = defaultDate.toISOString().slice(0, 16)
  showExtendDialog.value = true
}

async function handleExtendInvite() {
  extendError.value = ''
  extendSubmitting.value = true
  try {
    const expiresAt = new Date(newExpiryDate.value).toISOString()
    await apiExtendInvite(extendInviteId.value, expiresAt)
    showExtendDialog.value = false
    await loadInvites()
  } catch (err: unknown) {
    const msg = handleApiError(err)
    if (msg) extendError.value = msg
  } finally {
    extendSubmitting.value = false
  }
}

function canExtend(invite: InviteListItem): boolean {
  return invite.status === 'PENDING'
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
  <section class="st-page admin-invites-view">
    <StPageHeader title="邀请管理" description="创建和管理一次性邀请链接">
      <template #actions>
        <button class="st-button st-button--primary" type="button" @click="openCreateDialog">创建邀请</button>
      </template>
    </StPageHeader>

    <div v-if="error" role="alert" class="auth-error">
      {{ error }}
    </div>

    <div v-if="loading" class="auth-loading">加载中...</div>

    <div v-if="isDesktop && invites.length > 0" class="st-table-wrapper admin-table-fallback">
      <table class="st-table">
        <thead>
          <tr>
            <th>状态</th>
            <th>角色</th>
            <th>有效期</th>
            <th>使用次数</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="invite in invites" :key="`table-${invite.id}`">
            <td>{{ formatStatus(invite.status) }}</td>
            <td>{{ formatRole(invite.role) }}</td>
            <td>{{ new Date(invite.expiresAt).toLocaleString() }}</td>
            <td>{{ invite.useCount }}/{{ invite.maxUses }}</td>
            <td class="st-table__actions">
              <button
                v-if="canExtend(invite)"
                class="st-button st-button--text"
                type="button"
                @click="openExtendDialog(invite.id, invite.expiresAt)"
              >延长</button>
              <button
                v-if="invite.status === 'PENDING'"
                class="st-button st-button--text"
                type="button"
                @click="handleRevokeInvite(invite.id)"
              >撤销</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ul v-if="!isDesktop && invites.length > 0" class="invite-list">
      <li v-for="invite in invites" :key="invite.id" class="invite-item">
        <div class="invite-item-info">
          <el-tag :type="formatStatusType(invite.status) as any" size="small">
            {{ formatStatus(invite.status) }}
          </el-tag>
          <span class="invite-role">{{ formatRole(invite.role) }}</span>
          <span class="invite-expires">
            有效期至：{{ new Date(invite.expiresAt).toLocaleString() }}
          </span>
          <span v-if="invite.maxUses > 1" class="invite-uses">
            使用次数：{{ invite.useCount }}/{{ invite.maxUses }}
          </span>
          <span v-if="invite.acceptedBy && invite.acceptedBy.length > 0" class="invite-accepted-by">
            接受者：{{ invite.acceptedBy.join(', ') }}
          </span>
        </div>
        <div class="invite-item-actions">
          <button
            v-if="canExtend(invite)"
            class="member-action-btn"
            @click="openExtendDialog(invite.id, invite.expiresAt)"
          >
            延长
          </button>
          <button
            v-if="invite.status === 'PENDING'"
            class="member-action-btn"
            @click="handleRevokeInvite(invite.id)"
          >
            撤销
          </button>
        </div>
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

        <div class="form-field">
          <label for="inviteMaxUses">最大使用次数</label>
          <input
            id="inviteMaxUses"
            v-model.number="newMaxUses"
            type="number"
            min="1"
            max="100"
          />
        </div>
      </form>
      <template #footer>
        <button class="auth-logout-btn" style="width: auto;" @click="showCreateDialog = false">取消</button>
        <button class="auth-submit" style="width: auto;" :disabled="createSubmitting" @click="handleCreateInvite">
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
        <button
          class="result-copy-btn"
          :disabled="copied"
          @click="copyToClipboard(resultInviteLink)"
        >
          {{ copied ? '已复制' : '复制链接' }}
        </button>
      </div>
      <template #footer>
        <button class="auth-submit" style="width: auto;" @click="closeResultDialog">
          确定
        </button>
      </template>
    </el-dialog>

    <!-- Extend invite dialog -->
    <el-dialog
      v-model="showExtendDialog"
      title="延长邀请有效期"
      :close-on-click-modal="false"
      width="400px"
    >
      <form class="auth-form" @submit.prevent="handleExtendInvite">
        <div v-if="extendError" role="alert" class="auth-error">
          {{ extendError }}
        </div>

        <div class="form-field">
          <label for="newExpiry">新的过期时间</label>
          <input
            id="newExpiry"
            v-model="newExpiryDate"
            type="datetime-local"
            :min="new Date().toISOString().slice(0, 16)"
          />
        </div>
      </form>
      <template #footer>
        <button class="auth-logout-btn" style="width: auto;" @click="showExtendDialog = false">取消</button>
        <button class="auth-submit" style="width: auto;" :disabled="extendSubmitting" @click="handleExtendInvite">
          {{ extendSubmitting ? '提交中...' : '确认延长' }}
        </button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.invite-uses {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  margin-left: 0.5rem;
}

.invite-accepted-by {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  margin-left: 0.5rem;
}

.invite-item-actions {
  display: flex;
  gap: 0.5rem;
}
</style>
