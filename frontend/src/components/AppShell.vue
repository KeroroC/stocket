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

const props = defineProps<{
  account: CurrentAccount
}>()

const emit = defineEmits<{
  logout: []
  forcePasswordChange: []
}>()

type ViewName = 'account' | 'items' | 'inventory' | 'reminders' | 'members' | 'invites' | 'categories' | 'locations' | 'notification-settings' | 'delivery-failures'

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
  <div class="shell-layout">
    <nav class="shell-sidebar" aria-label="主导航">
      <div class="shell-user">
        <div class="shell-user-name">{{ account.displayName }}</div>
        <div class="shell-user-role">{{ account.role }}</div>
      </div>

      <ul class="shell-nav">
        <li><button :class="['shell-nav-item', { active: currentView === 'items' }]" @click="navigateTo('items')">物品目录</button></li>
        <li><button :class="['shell-nav-item', { active: currentView === 'inventory' }]" @click="navigateTo('inventory')">库存台账</button></li>
        <li><button :class="['shell-nav-item', { active: currentView === 'reminders' }]" @click="navigateTo('reminders')">提醒中心</button></li>
        <li>
          <button
            :class="['shell-nav-item', { active: currentView === 'account' }]"
            @click="navigateTo('account')"
          >
            我的账户
          </button>
        </li>
        <li v-if="isAdmin">
          <button
            :class="['shell-nav-item', { active: currentView === 'members' }]"
            @click="navigateTo('members')"
          >
            成员管理
          </button>
        </li>
        <li v-if="isAdmin"><button :class="['shell-nav-item', { active: currentView === 'notification-settings' }]" @click="navigateTo('notification-settings')">通知设置</button></li>
        <li v-if="isAdmin"><button :class="['shell-nav-item', { active: currentView === 'delivery-failures' }]" @click="navigateTo('delivery-failures')">通知失败</button></li>
        <li v-if="isAdmin"><button :class="['shell-nav-item', { active: currentView === 'categories' }]" @click="navigateTo('categories')">分类管理</button></li>
        <li v-if="isAdmin"><button :class="['shell-nav-item', { active: currentView === 'locations' }]" @click="navigateTo('locations')">位置管理</button></li>
        <li v-if="isAdmin">
          <button
            :class="['shell-nav-item', { active: currentView === 'invites' }]"
            @click="navigateTo('invites')"
          >
            邀请管理
          </button>
        </li>
      </ul>

      <div class="shell-sidebar-footer">
        <button class="auth-logout-btn" @click="handleLogout">退出登录</button>
      </div>
    </nav>

    <main class="shell-content">
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
    </main>
  </div>
</template>
