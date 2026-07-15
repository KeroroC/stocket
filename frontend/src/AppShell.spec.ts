import { cleanup, render, screen, within } from '@testing-library/vue'
import { createMemoryHistory } from 'vue-router'
import { ref } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import type { AuthState } from './auth/AuthState'
import { createStocketRouter } from './router'
import MobileTabBar from './components/navigation/MobileTabBar.vue'
import DesktopSidebar from './components/navigation/DesktopSidebar.vue'

afterEach(cleanup)

const account = {
  id: 'account-1',
  username: 'member',
  displayName: '家庭成员',
  role: 'MEMBER',
}

describe('移动 PWA 应用壳', () => {
  it('提供五个移动主导航并唯一标记中央入库入口和当前页', async () => {
    const authState = ref<AuthState>({ kind: 'authenticated', account })
    const router = createStocketRouter(authState, createMemoryHistory())
    await router.push('/items')
    await router.isReady()

    render(MobileTabBar, { global: { plugins: [router] } })

    const navigation = screen.getByRole('navigation', { name: '移动主导航' })
    for (const label of ['首页', '物品', '入库', '提醒', '我的']) {
      expect(within(navigation).getByRole('link', { name: label })).toBeInTheDocument()
    }
    expect(within(navigation).getAllByRole('link', { name: '入库' })).toHaveLength(1)
    expect(within(navigation).getByRole('link', { name: '物品' }))
      .toHaveAttribute('aria-current', 'page')
  })

  it('桌面使用同一路由展示侧栏导航', async () => {
    const authState = ref<AuthState>({ kind: 'authenticated', account })
    const router = createStocketRouter(authState, createMemoryHistory())
    await router.push('/')
    await router.isReady()

    render(DesktopSidebar, {
      props: { account },
      global: { plugins: [router] },
    })

    const navigation = screen.getByRole('navigation', { name: '桌面主导航' })
    expect(within(navigation).getByRole('link', { name: '首页' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('家庭成员')).toBeInTheDocument()
  })

  it('管理员桌面侧栏提供全部管理入口', async () => {
    const adminAccount = { ...account, role: 'ADMIN' }
    const authState = ref<AuthState>({ kind: 'authenticated', account: adminAccount })
    const router = createStocketRouter(authState, createMemoryHistory())
    await router.push('/')
    await router.isReady()

    render(DesktopSidebar, { props: { account: adminAccount }, global: { plugins: [router] } })

    for (const [label, href] of [
      ['成员管理', '/admin/members'],
      ['邀请管理', '/admin/invites'],
      ['分类管理', '/admin/categories'],
      ['位置管理', '/admin/locations'],
      ['通知失败', '/admin/delivery-failures'],
    ]) {
      expect(screen.getByRole('link', { name: label })).toHaveAttribute('href', href)
    }
  })

  it('管理员可以访问成员、邀请和通知失败路由，普通成员会被拒绝', async () => {
    const adminAccount = { ...account, role: 'ADMIN' }
    const adminAuthState = ref<AuthState>({ kind: 'authenticated', account: adminAccount })
    const adminRouter = createStocketRouter(adminAuthState, createMemoryHistory())

    for (const path of ['/admin/members', '/admin/invites', '/admin/delivery-failures']) {
      await adminRouter.push(path)
      expect(adminRouter.currentRoute.value.path).toBe(path)
    }

    const memberAuthState = ref<AuthState>({ kind: 'authenticated', account })
    const memberRouter = createStocketRouter(memberAuthState, createMemoryHistory())
    await memberRouter.push('/admin/members')
    expect(memberRouter.currentRoute.value.path).toBe('/')
  })

  it('未登录时阻止进入认证路由并重定向登录', async () => {
    const authState = ref<AuthState>({ kind: 'anonymous' })
    const router = createStocketRouter(authState, createMemoryHistory())

    await router.push('/items')

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('强制改密状态只能进入改密页', async () => {
    const authState = ref<AuthState>({ kind: 'password-change-required', account })
    const router = createStocketRouter(authState, createMemoryHistory())

    await router.push('/reminders')
    expect(router.currentRoute.value.path).toBe('/change-password')

    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/change-password')
  })
})
