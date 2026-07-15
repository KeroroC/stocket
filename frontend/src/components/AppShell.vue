<script setup lang="ts">
import { ref } from 'vue'
import type { CurrentAccount } from '../auth/AuthState'
import AccountView from '../views/AccountView.vue'
import AdminMembersView from '../views/AdminMembersView.vue'
import AdminInvitesView from '../views/AdminInvitesView.vue'
import CategoryAdminView from '../views/CategoryAdminView.vue'
import LocationAdminView from '../views/LocationAdminView.vue'
import ItemsView from '../views/ItemsView.vue'
import InventoryEntryView from '../views/InventoryEntryView.vue'
import RemindersView from '../views/RemindersView.vue'
import NotificationSettingsView from '../views/NotificationSettingsView.vue'
import DeliveryFailuresView from '../views/DeliveryFailuresView.vue'
import AuditLogView from '../views/AuditLogView.vue'
import DiagnosticsView from '../views/DiagnosticsView.vue'

const props = defineProps<{
  account: CurrentAccount
}>()

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
}>()

type ViewName = 'account' | 'items' | 'inventory' | 'reminders' | 'members' | 'invites' | 'categories' | 'locations' | 'notification-settings' | 'delivery-failures' | 'audit-logs' | 'diagnostics'

const currentView = ref<ViewName>('account')

const isAdmin = props.account.role === 'ADMIN'

function navigateTo(view: ViewName) {
  currentView.value = view
}

function handleLogout() {
  emit('logout')
}

function handleChildLogout() {
  emit('logout')
}

function handleForcePasswordChange() {
  emit('forcePasswordChange')
}
</script>

<template>
  <el-container class="shell-layout">
    <el-aside class="shell-sidebar" width="240px">
      <div class="shell-user">
        <div class="shell-user-name">{{ account.displayName }}</div>
        <el-tag class="shell-user-role" size="small">{{ account.role }}</el-tag>
      </div>
      <nav aria-label="主导航"><el-menu class="shell-nav" :default-active="currentView" @select="navigateTo($event as ViewName)"><el-menu-item index="items">物品目录</el-menu-item><el-menu-item index="inventory">库存台账</el-menu-item><el-menu-item index="reminders">提醒中心</el-menu-item><el-menu-item index="account">我的账户</el-menu-item><template v-if="isAdmin"><el-menu-item index="members">成员管理</el-menu-item><el-menu-item index="notification-settings">通知设置</el-menu-item><el-menu-item index="delivery-failures">通知失败</el-menu-item><el-menu-item index="categories">分类管理</el-menu-item><el-menu-item index="locations">位置管理</el-menu-item><el-menu-item index="audit-logs">审计日志</el-menu-item><el-menu-item index="diagnostics">系统诊断</el-menu-item><el-menu-item index="invites">邀请管理</el-menu-item></template></el-menu></nav>
      <div class="shell-sidebar-footer">
        <el-button plain @click="handleLogout">退出登录</el-button>
      </div>
    </el-aside>

    <el-main class="shell-content">
      <AccountView
        v-if="currentView === 'account'"
        :account="account"
        @logout="handleChildLogout"
        @force-password-change="handleForcePasswordChange"
      />
      <ItemsView v-else-if="currentView === 'items'" :role="account.role" />
      <InventoryEntryView v-else-if="currentView === 'inventory'" :role="account.role" />
      <RemindersView v-else-if="currentView === 'reminders'" />
      <AdminMembersView
        v-else-if="currentView === 'members'"
        @logout="handleChildLogout"
        @force-password-change="handleForcePasswordChange"
      />
      <AdminInvitesView
        v-else-if="currentView === 'invites'"
        @logout="handleChildLogout"
        @force-password-change="handleForcePasswordChange"
      />
      <CategoryAdminView v-else-if="currentView === 'categories'" />
      <LocationAdminView v-else-if="currentView === 'locations'" />
      <NotificationSettingsView v-else-if="currentView === 'notification-settings'" />
      <DeliveryFailuresView v-else-if="currentView === 'delivery-failures'" />
      <AuditLogView v-else-if="currentView === 'audit-logs'" />
      <DiagnosticsView v-else-if="currentView === 'diagnostics'" />
    </el-main>
  </el-container>
</template>
