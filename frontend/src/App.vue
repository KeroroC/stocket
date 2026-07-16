<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuth } from './auth/useAuth'
import SetupView from './views/SetupView.vue'
import LoginView from './views/LoginView.vue'
import InviteAcceptView from './views/InviteAcceptView.vue'
import PasswordChangeView from './views/PasswordChangeView.vue'
import PwaAppShell from './components/PwaAppShell.vue'

const { state, bootstrap, logout, passwordChanged, initialize } = useAuth()

const inviteToken = computed(() => {
  const match = window.location.pathname.match(/^\/invite\/([^/]+)$/)
  return match ? match[1] : null
})

const inviteCompleted = ref(false)
const showInviteView = computed(() => inviteToken.value !== null && !inviteCompleted.value)

onMounted(() => {
  if (!showInviteView.value) {
    bootstrap()
  }
})

function handleSetupSuccess() {
  bootstrap()
}

function handleLoginSuccess() {
  bootstrap()
}

function handleInviteSuccess() {
  inviteCompleted.value = true
  window.history.replaceState({}, '', '/login')
  bootstrap()
}

function handlePasswordChanged() {
  passwordChanged()
}

function handleLogout() {
  logout()
}

function handleForcePasswordChange() {
  // Re-bootstrap to transition to password-change-required state
  bootstrap()
}
</script>

<template>
  <main v-loading="state.kind === 'checking-setup'" :class="['app-shell', { 'app-shell--authenticated': state.kind === 'authenticated' }]">
    <div v-if="state.kind !== 'authenticated'" class="auth-brand" aria-label="Stocket 家庭库存">
      <span class="auth-brand__mark" aria-hidden="true">S</span>
      <span class="auth-brand__copy">
        <strong>Stocket</strong>
        <small>把家里的物品安稳放在心里</small>
      </span>
    </div>

    <!-- invite view takes priority when URL matches /invite/{token} -->
    <InviteAcceptView
      v-if="showInviteView && inviteToken"
      :token="inviteToken"
      @success="handleInviteSuccess"
    />

    <!-- checking-setup -->
    <el-card v-else-if="state.kind === 'checking-setup'" class="auth-card"><el-skeleton :rows="3" animated /></el-card>

    <!-- setup-required -->
    <SetupView
      v-else-if="state.kind === 'setup-required'"
      @success="handleSetupSuccess"
    />

    <!-- anonymous (login) -->
    <LoginView
      v-else-if="state.kind === 'anonymous'"
      @success="handleLoginSuccess"
    />

    <!-- password-change-required -->
    <PasswordChangeView
      v-else-if="state.kind === 'password-change-required'"
      @success="handlePasswordChanged"
      @logout="handleLogout"
    />

    <!-- authenticated: full management shell -->
    <PwaAppShell
      v-else-if="state.kind === 'authenticated'"
      :account="state.account"
      @logout="handleLogout"
      @force-password-change="handleForcePasswordChange"
    />
  </main>
</template>
