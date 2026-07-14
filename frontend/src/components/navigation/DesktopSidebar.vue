<script setup lang="ts">
import { Bell, Box, DocumentChecked, FirstAidKit, HomeFilled, Plus, User } from '@element-plus/icons-vue'
import type { CurrentAccount } from '../../auth/AuthState'

defineProps<{ account: CurrentAccount }>()
const emit = defineEmits<{ logout: [] }>()

const items = [
  { to: '/', label: '首页', icon: HomeFilled },
  { to: '/items', label: '物品', icon: Box },
  { to: '/receive', label: '入库', icon: Plus },
  { to: '/reminders', label: '提醒', icon: Bell },
  { to: '/profile', label: '我的', icon: User },
]
const adminItems = [
  { to: '/admin/audit-logs', label: '审计日志', icon: DocumentChecked },
  { to: '/admin/diagnostics', label: '系统诊断', icon: FirstAidKit },
]
</script>

<template>
  <aside class="desktop-sidebar">
    <div class="desktop-sidebar__brand">Stocket</div>
    <div class="desktop-sidebar__account">
      <strong>{{ account.displayName }}</strong>
      <span>{{ account.role }}</span>
    </div>
    <nav aria-label="桌面主导航">
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="desktop-sidebar__link">
        <component :is="item.icon" aria-hidden="true" />
        <span>{{ item.label }}</span>
      </RouterLink>
      <RouterLink v-for="item in account.role === 'ADMIN' ? adminItems : []" :key="item.to" :to="item.to" class="desktop-sidebar__link">
        <component :is="item.icon" aria-hidden="true" /><span>{{ item.label }}</span>
      </RouterLink>
    </nav>
    <button class="desktop-sidebar__logout" type="button" @click="emit('logout')">退出登录</button>
  </aside>
</template>
