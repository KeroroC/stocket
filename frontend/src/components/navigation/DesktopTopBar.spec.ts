import { cleanup, fireEvent, render, screen, within } from '@testing-library/vue'
import { createMemoryHistory } from 'vue-router'
import { ref } from 'vue'
import { afterEach, describe, expect, it } from 'vitest'
import type { AuthState } from '../../auth/AuthState'
import { createStocketRouter } from '../../router'
import DesktopTopBar from './DesktopTopBar.vue'

afterEach(cleanup)

const member = {
  id: 'a1',
  username: 'member',
  displayName: '家庭成员',
  role: 'MEMBER',
}

async function renderTopBar(account = member) {
  const authState = ref<AuthState>({ kind: 'authenticated', account })
  const router = createStocketRouter(authState, createMemoryHistory())
  await router.push('/')
  await router.isReady()
  return render(DesktopTopBar, {
    props: { account },
    global: { plugins: [router] },
  })
}

describe('DesktopTopBar', () => {
  it('展示全局搜索、快捷入库与账户菜单，并可退出', async () => {
    const { emitted } = await renderTopBar()
    expect(screen.getByRole('searchbox', { name: '全局搜索' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '快捷入库' })).toHaveAttribute('href', '/receive')

    await fireEvent.click(screen.getByRole('button', { name: /家庭成员/ }))
    const menu = screen.getByRole('menu')
    expect(within(menu).getByRole('menuitem', { name: '我的账户' })).toHaveAttribute('href', '/profile')
    expect(within(menu).getByRole('menuitem', { name: '通知设置' })).toHaveAttribute('href', '/notification-settings')
    await fireEvent.click(within(menu).getByRole('menuitem', { name: '退出登录' }))
    expect(emitted().logout).toBeTruthy()
  })

  it('VIEWER 不显示快捷入库', async () => {
    await renderTopBar({ ...member, role: 'VIEWER', displayName: '只读成员' })
    expect(screen.queryByRole('link', { name: '快捷入库' })).not.toBeInTheDocument()
  })
})
