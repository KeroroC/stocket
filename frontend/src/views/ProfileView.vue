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
    <nav class="profile-view__actions" aria-label="个人设置">
      <RouterLink to="/notification-settings">通知设置</RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" to="/admin/categories">分类管理</RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" to="/admin/locations">位置管理</RouterLink>
    </nav>
    <button class="st-button" type="button" @click="emit('logout')">退出登录</button>
  </section>
</template>
