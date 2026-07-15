<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { CurrentAccount } from '../../auth/AuthState'
import GlobalSearch from '../search/GlobalSearch.vue'

const props = defineProps<{ account: CurrentAccount }>()
const emit = defineEmits<{ logout: [] }>()

const menuOpen = ref(false)
const canReceive = ['ADMIN', 'MEMBER'].includes(props.account.role)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

function onDocumentClick(event: MouseEvent) {
  const target = event.target as Node | null
  const root = document.querySelector('.desktop-topbar__account')
  if (root && target && !root.contains(target)) closeMenu()
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <header class="desktop-topbar" aria-label="桌面顶栏">
    <div class="desktop-topbar__search">
      <GlobalSearch compact />
    </div>
    <div class="desktop-topbar__actions">
      <RouterLink v-if="canReceive" class="st-button st-button--primary" to="/receive">快捷入库</RouterLink>
      <div class="desktop-topbar__account">
        <button
          type="button"
          class="desktop-topbar__account-btn"
          :aria-expanded="menuOpen"
          aria-haspopup="menu"
          :aria-label="`${account.displayName} 账户菜单`"
          @click.stop="toggleMenu"
        >
          <strong>{{ account.displayName }}</strong>
          <span>{{ account.role }}</span>
        </button>
        <div v-if="menuOpen" class="desktop-topbar__menu" role="menu">
          <RouterLink role="menuitem" to="/profile" @click="closeMenu">我的账户</RouterLink>
          <RouterLink role="menuitem" to="/notification-settings" @click="closeMenu">通知设置</RouterLink>
          <button role="menuitem" type="button" @click="emit('logout')">退出登录</button>
        </div>
      </div>
    </div>
  </header>
</template>
