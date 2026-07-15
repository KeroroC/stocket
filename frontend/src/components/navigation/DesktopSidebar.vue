<script setup lang="ts">
import { useRoute } from 'vue-router'
import type { CurrentAccount } from '../../auth/AuthState'
import { visibleNavGroups } from './navConfig'

const props = defineProps<{ account: CurrentAccount }>()
const groups = visibleNavGroups(props.account.role)
const route = useRoute()
</script>

<template>
  <el-aside class="desktop-sidebar" width="256px">
    <div class="desktop-sidebar__brand"><span class="desktop-sidebar__logo">S</span><span>Stocket</span></div>
    <nav aria-label="桌面主导航">
      <el-scrollbar>
        <el-menu :default-active="route.path">
          <el-menu-item-group v-for="group in groups" :key="group.id" :title="group.label">
            <el-menu-item v-for="item in group.items" :key="item.to" :index="item.to">
              <RouterLink :to="item.to" class="desktop-sidebar__link">
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </RouterLink>
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
      </el-scrollbar>
    </nav>
  </el-aside>
</template>
