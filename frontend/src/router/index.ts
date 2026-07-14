import type { Ref } from 'vue'
import {
  createRouter,
  createWebHistory,
  type RouterHistory,
} from 'vue-router'
import type { AuthState } from '../auth/AuthState'
import ItemsView from '../views/ItemsView.vue'
import ReceiveWizardView from '../views/ReceiveWizardView.vue'
import RemindersView from '../views/RemindersView.vue'
import ProfileView from '../views/ProfileView.vue'
import InventoryEntryView from '../views/InventoryEntryView.vue'
import NotificationSettingsView from '../views/NotificationSettingsView.vue'
import LoginView from '../views/LoginView.vue'
import PasswordChangeView from '../views/PasswordChangeView.vue'
import HomeView from '../views/HomeView.vue'

export function createStocketRouter(authState: Ref<AuthState>, history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true } },
      { path: '/items', name: 'items', component: ItemsView, meta: { requiresAuth: true } },
      { path: '/receive', name: 'receive', component: ReceiveWizardView, meta: { requiresAuth: true, roles: ['ADMIN', 'MEMBER'] } },
      { path: '/reminders', name: 'reminders', component: RemindersView, meta: { requiresAuth: true } },
      { path: '/profile', name: 'profile', component: ProfileView, meta: { requiresAuth: true } },
      { path: '/inventory/:id?', name: 'inventory', component: InventoryEntryView, meta: { requiresAuth: true, roles: ['ADMIN', 'MEMBER', 'VIEWER'] } },
      { path: '/notification-settings', name: 'notification-settings', component: NotificationSettingsView, meta: { requiresAuth: true } },
      { path: '/login', name: 'login', component: LoginView },
      { path: '/change-password', name: 'change-password', component: PasswordChangeView },
    ],
  })

  router.beforeEach((to) => {
    const state = authState.value
    if (state.kind === 'checking-setup' || state.kind === 'setup-required') {
      return true
    }
    if (state.kind === 'password-change-required' && to.name !== 'change-password') {
      return { name: 'change-password' }
    }
    if (to.meta.requiresAuth && state.kind !== 'authenticated') {
      return state.kind === 'password-change-required'
        ? { name: 'change-password' }
        : { name: 'login', query: { redirect: to.fullPath } }
    }
    if (to.name === 'login' && state.kind === 'authenticated') {
      return { name: 'home' }
    }
    return true
  })

  return router
}
