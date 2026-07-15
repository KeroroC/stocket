import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import ProfileView from './ProfileView.vue'

vi.mock('./AccountView.vue', () => ({ default: { template: '<section>账号与会话</section>' } }))
afterEach(cleanup)

describe('ProfileView', () => {
  it('展示账号、会话和通知入口并转发退出', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: ProfileView },
        { path: '/notification-settings', component: { template: '<div>通知设置</div>' } },
      ],
    })
    const { emitted } = render(ProfileView, {
      props: { account: { id: 'a1', username: 'member', displayName: '成员', role: 'MEMBER' } },
      global: { plugins: [router] },
    })
    expect(screen.getByText('账号与会话')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '通知设置' })).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '退出登录' }))
    expect(emitted().logout).toBeTruthy()
  })

  it('管理员可以从我的页面进入分类和位置管理', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: ProfileView },
        { path: '/notification-settings', component: { template: '<div />' } },
        { path: '/admin/categories', component: { template: '<div />' } },
        { path: '/admin/locations', component: { template: '<div />' } },
      ],
    })
    render(ProfileView, {
      props: { account: { id: 'a1', username: 'admin', displayName: '管理员', role: 'ADMIN' } },
      global: { plugins: [router] },
    })

    expect(screen.getByRole('link', { name: '分类管理' })).toHaveAttribute('href', '/admin/categories')
    expect(screen.getByRole('link', { name: '位置管理' })).toHaveAttribute('href', '/admin/locations')
  })
})
