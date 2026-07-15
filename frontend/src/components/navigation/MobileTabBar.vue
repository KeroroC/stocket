<script setup lang="ts">
import { Bell, Box, HomeFilled, Plus, User } from '@element-plus/icons-vue'
import { useRoute } from 'vue-router'

const items = [
  { to: '/', label: '首页', icon: HomeFilled },
  { to: '/items', label: '物品', icon: Box },
  { to: '/receive', label: '入库', icon: Plus, primary: true },
  { to: '/reminders', label: '提醒', icon: Bell },
  { to: '/profile', label: '我的', icon: User },
]
const route = useRoute()
</script>

<template>
  <nav class="mobile-tab-bar" aria-label="移动主导航">
    <el-menu mode="horizontal" :default-active="route.path" :ellipsis="false">
    <el-menu-item
      v-for="item in items"
      :key="item.to"
      :index="item.to"
      :class="['mobile-tab-bar__item', { 'mobile-tab-bar__item--primary': item.primary }]"
      :aria-label="item.label"
    >
      <RouterLink :to="item.to">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </RouterLink>
    </el-menu-item>
    </el-menu>
  </nav>
</template>
