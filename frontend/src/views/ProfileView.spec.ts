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

  it('管理员可以从我的页面进入全部管理功能', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: ProfileView },
        { path: '/notification-settings', component: { template: '<div />' } },
        { path: '/admin/members', component: { template: '<div />' } },
        { path: '/admin/invites', component: { template: '<div />' } },
        { path: '/admin/categories', component: { template: '<div />' } },
        { path: '/admin/locations', component: { template: '<div />' } },
        { path: '/admin/delivery-failures', component: { template: '<div />' } },
        { path: '/admin/audit-logs', component: { template: '<div />' } },
        { path: '/admin/diagnostics', component: { template: '<div />' } },
      ],
    })
    render(ProfileView, {
      props: { account: { id: 'a1', username: 'admin', displayName: '管理员', role: 'ADMIN' } },
      global: { plugins: [router] },
    })

    for (const [label, href] of [
      ['成员管理', '/admin/members'],
      ['邀请管理', '/admin/invites'],
      ['分类管理', '/admin/categories'],
      ['位置管理', '/admin/locations'],
      ['通知失败', '/admin/delivery-failures'],
      ['审计日志', '/admin/audit-logs'],
      ['系统诊断', '/admin/diagnostics'],
    ]) {
      expect(screen.getByRole('link', { name: label })).toHaveAttribute('href', href)
    }
  })
})
