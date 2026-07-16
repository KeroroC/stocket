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
      <RouterLink class="profile-view__action" aria-label="通知设置" to="/notification-settings">
        <span>通知设置</span><small>管理浏览器通知与发送渠道</small>
      </RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" class="profile-view__action" aria-label="成员管理" to="/admin/members">
        <span>成员管理</span><small>维护角色与访问状态</small>
      </RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" class="profile-view__action" aria-label="邀请管理" to="/admin/invites">
        <span>邀请管理</span><small>创建和维护邀请链接</small>
      </RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" class="profile-view__action" aria-label="分类管理" to="/admin/categories">
        <span>分类管理</span><small>整理物品分类结构</small>
      </RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" class="profile-view__action" aria-label="位置管理" to="/admin/locations">
        <span>位置管理</span><small>维护存放位置与位置码</small>
      </RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" class="profile-view__action" aria-label="通知失败" to="/admin/delivery-failures">
        <span>通知失败</span><small>处理永久失败的投递</small>
      </RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" class="profile-view__action" aria-label="审计日志" to="/admin/audit-logs">
        <span>审计日志</span><small>追踪关键操作与请求</small>
      </RouterLink>
      <RouterLink v-if="account.role === 'ADMIN'" class="profile-view__action" aria-label="系统诊断" to="/admin/diagnostics">
        <span>系统诊断</span><small>查看安全的运行状态</small>
      </RouterLink>
    </nav>
    <el-button type="danger" plain @click="emit('logout')">退出登录</el-button>
  </section>
</template>
