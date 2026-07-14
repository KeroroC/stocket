import { defineComponent, h, type Ref } from 'vue'
import {
  createRouter,
  createWebHistory,
  type RouterHistory,
} from 'vue-router'
import type { AuthState } from '../auth/AuthState'
import ItemsView from '../views/ItemsView.vue'
import ReceiveWizardView from '../views/ReceiveWizardView.vue'
import RemindersView from '../views/RemindersView.vue'
import AccountView from '../views/AccountView.vue'
import LoginView from '../views/LoginView.vue'
import PasswordChangeView from '../views/PasswordChangeView.vue'

const HomePlaceholder = defineComponent({
  name: 'HomePlaceholder',
  setup: () => () => h('section', { class: 'route-placeholder' }, [
    h('h1', '首页'),
    h('p', '家庭资产任务概览将在此显示。'),
  ]),
})

export function createStocketRouter(authState: Ref<AuthState>, history: RouterHistory = createWebHistory()) {
  const router = createRouter({
    history,
    routes: [
      { path: '/', name: 'home', component: HomePlaceholder, meta: { requiresAuth: true } },
      { path: '/items', name: 'items', component: ItemsView, meta: { requiresAuth: true } },
      { path: '/receive', name: 'receive', component: ReceiveWizardView, meta: { requiresAuth: true } },
      { path: '/reminders', name: 'reminders', component: RemindersView, meta: { requiresAuth: true } },
      { path: '/profile', name: 'profile', component: AccountView, meta: { requiresAuth: true } },
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
