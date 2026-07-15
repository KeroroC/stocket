<script setup lang="ts">
import { inject } from 'vue'
import { routerKey } from 'vue-router'
import type { CurrentAccount } from '../auth/AuthState'
import DesktopSidebar from './navigation/DesktopSidebar.vue'
import DesktopTopBar from './navigation/DesktopTopBar.vue'
import MobileTabBar from './navigation/MobileTabBar.vue'
import LegacyAppShell from './AppShell.vue'

defineProps<{ account: CurrentAccount }>()

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
}>()

const hasRouter = Boolean(inject(routerKey, null))
</script>

<template>
  <LegacyAppShell
    v-if="!hasRouter"
    :account="account"
    @logout="emit('logout')"
    @force-password-change="emit('forcePasswordChange')"
  />
  <div v-else class="pwa-shell">
    <a class="skip-link" href="#main-content">跳到主内容</a>
    <DesktopSidebar :account="account" />
    <DesktopTopBar :account="account" @logout="emit('logout')" />
    <main id="main-content" class="pwa-shell__content" tabindex="-1">
      <RouterView v-slot="{ Component }">
        <component
          :is="Component"
          :account="account"
          :role="account.role"
          @logout="emit('logout')"
          @force-password-change="emit('forcePasswordChange')"
        />
      </RouterView>
    </main>
    <MobileTabBar />
  </div>
</template>
