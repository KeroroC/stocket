import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ProfileView from './ProfileView.vue'

vi.mock('./AccountView.vue', () => ({ default: { template: '<section>账号与会话</section>' } }))
afterEach(cleanup)

describe('ProfileView', () => {
  it('展示账号、会话和通知入口并转发退出', async () => {
    const { emitted } = render(ProfileView, { props: { account: { id: 'a1', username: 'member', displayName: '成员', role: 'MEMBER' } } })
    expect(screen.getByText('账号与会话')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '通知设置' })).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '退出登录' }))
    expect(emitted().logout).toBeTruthy()
  })
})
