<script setup lang="ts">
import type { CurrentAccount } from '../auth/AuthState'
import AccountView from './AccountView.vue'

defineProps<{ account: CurrentAccount }>()
const emit = defineEmits<{ logout: []; forcePasswordChange: []; profileUpdated: [] }>()
</script>

<template>
  <section class="profile-view">
    <AccountView
      :account="account"
      @logout="emit('logout')"
      @force-password-change="emit('forcePasswordChange')"
      @profile-updated="emit('profileUpdated')"
    />
    <nav aria-label="个人设置">
      <a href="/notification-settings">通知设置</a>
    </nav>
    <button type="button" @click="emit('logout')">退出登录</button>
  </section>
</template>
