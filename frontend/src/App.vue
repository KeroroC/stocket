<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuth } from './auth/useAuth'
import SetupView from './views/SetupView.vue'
import LoginView from './views/LoginView.vue'
import InviteAcceptView from './views/InviteAcceptView.vue'
import PasswordChangeView from './views/PasswordChangeView.vue'
import AppShell from './components/AppShell.vue'

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
  <main class="app-shell">
    <!-- invite view takes priority when URL matches /invite/{token} -->
    <InviteAcceptView
      v-if="showInviteView && inviteToken"
      :token="inviteToken"
      @success="handleInviteSuccess"
    />

    <!-- checking-setup -->
    <section v-else-if="state.kind === 'checking-setup'" class="auth-card">
      <p>正在检查身份状态...</p>
    </section>

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
    <AppShell
      v-else-if="state.kind === 'authenticated'"
      :account="state.account"
      @logout="handleLogout"
      @force-password-change="handleForcePasswordChange"
    />
  </main>
</template>
