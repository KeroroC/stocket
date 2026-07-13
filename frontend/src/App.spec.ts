import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

let cookieSpy: ReturnType<typeof vi.spyOn>

beforeEach(() => {
  cookieSpy = vi.spyOn(document, 'cookie', 'get').mockReturnValue('XSRF-TOKEN=abc123')
  vi.spyOn(document, 'cookie', 'set').mockImplementation(() => {})
})

afterEach(() => {
  cleanup()
  cookieSpy.mockRestore()
  vi.restoreAllMocks()
})

function accountResponse(overrides: Record<string, unknown> = {}) {
  return {
    id: 'a1',
    username: 'admin',
    displayName: '管理员',
    email: null,
    role: 'ADMIN',
    mustChangePassword: false,
    ...overrides,
  }
}

describe('App', () => {
  it('setup-required 状态：显示初始化视图，刷新 CSRF 但不调用认证接口', async () => {
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: false }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByText('初始化家庭')).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalledWith('/api/v1/account', expect.anything())
    expect(fetch).toHaveBeenCalledWith('/api/v1/auth/csrf', expect.anything())
  })

  it('anonymous 状态：显示登录表单，调用 /account 和 /auth/csrf', async () => {
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
        })
      }
      if (url === '/api/v1/account') {
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ error: 'UNAUTHORIZED' }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/v1/account', expect.anything())
    expect(fetch).toHaveBeenCalledWith('/api/v1/auth/csrf', expect.anything())
  })

  it('authenticated 状态：显示用户名和退出登录按钮', async () => {
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      if (url === '/api/v1/account') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => accountResponse(),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByText('管理员')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '退出登录' })).toBeInTheDocument()
  })

  it('退出登录：调用 /auth/logout 后回到 anonymous', async () => {
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      if (url === '/api/v1/account') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => accountResponse(),
        })
      }
      if (url === '/api/v1/auth/logout') {
        return Promise.resolve({
          ok: true,
          status: 204,
          json: async () => ({}),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByText('管理员')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument()
    })
    expect(fetch).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({ method: 'POST' }))
  })

  it('登录后显示 authenticated 状态并刷新 CSRF', async () => {
    let accountCalls = 0
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      if (url === '/api/v1/account') {
        accountCalls++
        if (accountCalls === 1) {
          return Promise.resolve({
            ok: false,
            status: 401,
            json: async () => ({ error: 'UNAUTHORIZED' }),
          })
        }
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => accountResponse(),
        })
      }
      if (url === '/api/v1/auth/login') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            accountId: 'a1',
            username: 'admin',
            role: 'ADMIN',
          }),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    await fireEvent.update(screen.getByLabelText('用户名'), 'admin')
    await fireEvent.update(screen.getByLabelText('密码'), 'password')
    await fireEvent.click(screen.getByRole('button', { name: '登录' }))

    await waitFor(() => {
      expect(screen.getByText('管理员')).toBeInTheDocument()
    })

    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({ method: 'POST' }),
    )

    const loginCallIndex = fetch.mock.calls.findIndex(
      (c: unknown[]) => (c[0] as string) === '/api/v1/auth/login',
    )
    const csrfCallsAfterLogin = fetch.mock.calls
      .slice(loginCallIndex + 1)
      .filter((c: unknown[]) => (c[0] as string) === '/api/v1/auth/csrf')

    expect(csrfCallsAfterLogin.length).toBeGreaterThanOrEqual(1)
  })

  it('password-change-required 状态：显示修改密码提示', async () => {
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      if (url === '/api/v1/account') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => accountResponse({ mustChangePassword: true }),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByRole('heading', { name: '修改密码' })).toBeInTheDocument()
    expect(screen.getByText('请修改初始密码后再继续使用')).toBeInTheDocument()
  })

  it('bootstrap 调用顺序：setup/status → auth/csrf → account', async () => {
    const callHistory: string[] = []
    const fetch = vi.fn((url: string) => {
      callHistory.push(url)
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      if (url === '/api/v1/account') {
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ error: 'UNAUTHORIZED' }),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(callHistory[0]).toBe('/api/v1/setup/status')
    expect(callHistory[1]).toBe('/api/v1/auth/csrf')
    expect(callHistory[2]).toBe('/api/v1/account')
  })

  it('邀请成功后跳转到登录页面', async () => {
    // 模拟URL为邀请链接
    const originalPathname = window.location.pathname
    const originalPushState = window.history.pushState

    // 使用jsdom的方式模拟URL
    window.history.pushState({}, '', '/invite/test-token-123')

    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/invites/test-token-123/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({
            available: true,
            role: 'MEMBER',
            expiresAt: '2026-07-20T00:00:00Z',
          }),
        })
      }
      if (url === '/api/v1/invites/test-token-123/accept') {
        return Promise.resolve({
          ok: true,
          status: 201,
          json: async () => ({
            accountId: 'a1',
            memberId: 'm1',
          }),
        })
      }
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
        })
      }
      if (url === '/api/v1/account') {
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ error: 'UNAUTHORIZED' }),
        })
      }
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({}),
      })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    // 应该显示邀请接受视图
    expect(screen.getByRole('heading', { name: '接受邀请' })).toBeInTheDocument()

    // 填写表单
    const usernameInput = screen.getByLabelText(/用户名/)
    const displayNameInput = screen.getByLabelText(/显示名称/)
    const passwordInput = screen.getByLabelText('密码')
    const confirmPasswordInput = screen.getByLabelText(/确认密码/)

    await fireEvent.update(usernameInput, 'newuser')
    await fireEvent.update(displayNameInput, '新用户')
    await fireEvent.update(passwordInput, 'secureP@ss123456')
    await fireEvent.update(confirmPasswordInput, 'secureP@ss123456')

    // 检查表单填写是否正确
    expect(usernameInput).toHaveValue('newuser')
    expect(displayNameInput).toHaveValue('新用户')
    expect(passwordInput).toHaveValue('secureP@ss123456')
    expect(confirmPasswordInput).toHaveValue('secureP@ss123456')

    // 提交表单
    const submitButton = screen.getByRole('button', { name: /接受邀请/ })
    await fireEvent.click(submitButton)

    // 等待API调用完成
    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/invites/test-token-123/accept',
        expect.objectContaining({ method: 'POST' }),
      )
    })

    // 应该跳转到登录页面
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument()
    })

    // 恢复原始状态
    window.history.pushState({}, '', originalPathname)
  })
})
