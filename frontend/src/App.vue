<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAuth } from './auth/useAuth'
import SetupView from './views/SetupView.vue'
import LoginView from './views/LoginView.vue'
import InviteAcceptView from './views/InviteAcceptView.vue'
import PasswordChangeView from './views/PasswordChangeView.vue'

const { state, bootstrap, logout, passwordChanged, initialize } = useAuth()

const inviteToken = computed(() => {
  const match = window.location.pathname.match(/^\/invite\/([^/]+)$/)
  return match ? match[1] : null
})

const showInviteView = computed(() => inviteToken.value !== null)

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
  window.history.replaceState({}, '', '/login')
  bootstrap()
}

function handlePasswordChanged() {
  passwordChanged()
}

function handleLogout() {
  logout()
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

    <!-- authenticated -->
    <section v-else-if="state.kind === 'authenticated'" class="auth-card">
      <h1>{{ state.account.displayName }}</h1>
      <button class="auth-submit" @click="logout()">退出登录</button>
    </section>
  </main>
</template>
