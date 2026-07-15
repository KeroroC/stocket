<script setup lang="ts">
import { ArrowDown, Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { CurrentAccount } from '../../auth/AuthState'
import GlobalSearch from '../search/GlobalSearch.vue'

const props = defineProps<{ account: CurrentAccount }>()
const emit = defineEmits<{ logout: [] }>()

const canReceive = ['ADMIN', 'MEMBER'].includes(props.account.role)
const router = useRouter()

function handleCommand(command: string) {
  if (command === 'logout') emit('logout')
  else router.push(command)
}
</script>

<template>
  <el-header class="desktop-topbar" aria-label="桌面顶栏">
    <div class="desktop-topbar__search">
      <GlobalSearch compact />
    </div>
    <div class="desktop-topbar__actions">
      <RouterLink v-if="canReceive" v-slot="{ href, navigate }" custom to="/receive">
        <el-button tag="a" :href="href" type="primary" :icon="Plus" @click="navigate">快捷入库</el-button>
      </RouterLink>
      <el-dropdown trigger="click" @command="handleCommand">
        <el-button class="desktop-topbar__account-btn" :aria-label="`${account.displayName} 账户菜单`">
          <el-avatar :size="32">{{ account.displayName.slice(0, 1) }}</el-avatar>
          <span class="desktop-topbar__account-copy"><strong>{{ account.displayName }}</strong><small>{{ account.role }}</small></span>
          <el-icon><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="/profile">我的账户</el-dropdown-item>
            <el-dropdown-item command="/notification-settings">通知设置</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>
