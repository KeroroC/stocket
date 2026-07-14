<script setup lang="ts">
import { Bell, Box, HomeFilled, Plus, User } from '@element-plus/icons-vue'

const items = [
  { to: '/', label: '首页', icon: HomeFilled },
  { to: '/items', label: '物品', icon: Box },
  { to: '/receive', label: '入库', icon: Plus, primary: true },
  { to: '/reminders', label: '提醒', icon: Bell },
  { to: '/profile', label: '我的', icon: User },
]
</script>

<template>
  <nav class="mobile-tab-bar" aria-label="移动主导航">
    <RouterLink
      v-for="item in items"
      :key="item.to"
      :to="item.to"
      :class="['mobile-tab-bar__item', { 'mobile-tab-bar__item--primary': item.primary }]"
      :aria-label="item.label"
    >
      <component :is="item.icon" aria-hidden="true" />
      <span>{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>
