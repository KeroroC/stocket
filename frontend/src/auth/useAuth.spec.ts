import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App.vue'

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

describe('身份启动状态', () => {
  it('setup status 未初始化时显示 setup-required 视图', async () => {
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: false }),
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByText('初始化家庭')).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalledWith(
      '/api/v1/account',
      expect.anything(),
    )
    expect(fetch).not.toHaveBeenCalledWith(
      '/api/v1/auth/csrf',
      expect.anything(),
    )
  })

  it('已初始化但 /account 返回 401 时显示 anonymous 视图', async () => {
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
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledWith('/api/v1/auth/csrf', expect.anything())
  })

  it('已初始化且账户要求改密时显示 password-change-required 视图', async () => {
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
          json: async () => ({
            id: 'a1',
            username: 'admin',
            displayName: '管理员',
            role: 'ADMIN',
            mustChangePassword: true,
          }),
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByText('修改密码')).toBeInTheDocument()
  })

  it('已初始化且账户正常时显示 authenticated 视图', async () => {
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
          json: async () => ({
            id: 'a1',
            username: 'admin',
            displayName: '管理员',
            role: 'ADMIN',
            mustChangePassword: false,
          }),
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByText('管理员')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '退出登录' })).toBeInTheDocument()
  })

  it('登录后重新获取 CSRF', async () => {
    let accountCalls = 0
    const fetch = vi.fn((url: string) => {
      if (url === '/api/v1/setup/status') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ initialized: true }),
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
          json: async () => ({
            id: 'a1',
            username: 'admin',
            displayName: '管理员',
            role: 'ADMIN',
            mustChangePassword: false,
          }),
        })
      }
      if (url === '/api/v1/auth/csrf') {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({}),
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
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
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

    const loginCallIndex = fetch.mock.calls.findIndex(
      (c: unknown[]) => (c[0] as string) === '/api/v1/auth/login',
    )
    const csrfCallsAfterLogin = fetch.mock.calls
      .slice(loginCallIndex + 1)
      .filter((c: unknown[]) => (c[0] as string) === '/api/v1/auth/csrf')

    expect(csrfCallsAfterLogin.length).toBeGreaterThanOrEqual(1)
  })

  it('登录后 401 回到 anonymous 状态', async () => {
    let callCount = 0
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
        callCount++
        if (callCount === 1) {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({
              id: 'a1',
              username: 'admin',
              displayName: '管理员',
              role: 'ADMIN',
              mustChangePassword: false,
            }),
          })
        }
        return Promise.resolve({
          ok: false,
          status: 401,
          json: async () => ({ error: 'UNAUTHORIZED' }),
        })
      }
      if (url === '/api/v1/auth/logout') {
        return Promise.resolve({ ok: true, status: 204, json: async () => ({}) })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    expect(screen.getByText('管理员')).toBeInTheDocument()

    await fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument()
    })
  })

  it('bootstrap 中 /account 返回 401 时调用 refreshCsrf', async () => {
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
          ok: false,
          status: 401,
          json: async () => ({ error: 'UNAUTHORIZED' }),
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    const callHistory = fetch.mock.calls.map((c: unknown[]) => c[0] as string)

    expect(callHistory[0]).toBe('/api/v1/setup/status')
    expect(callHistory[1]).toBe('/api/v1/auth/csrf')
    expect(callHistory[2]).toBe('/api/v1/account')

    expect(callHistory).toContain('/api/v1/auth/csrf')
    expect(callHistory.indexOf('/api/v1/auth/csrf')).toBeLessThan(
      callHistory.indexOf('/api/v1/account'),
    )
  })

  it('CSRF 403 时刷新并最多重试一次', async () => {
    let postAttempts = 0
    const fetch = vi.fn((url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
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
          json: async () => ({
            id: 'a1',
            username: 'admin',
            displayName: '管理员',
            role: 'ADMIN',
            mustChangePassword: false,
          }),
        })
      }
      if (url === '/api/v1/auth/logout' && method === 'POST') {
        postAttempts++
        if (postAttempts === 1) {
          return Promise.resolve({
            ok: false,
            status: 403,
            json: async () => ({
              error: 'CSRF_TOKEN_INVALID',
              retryable: true,
            }),
          })
        }
        return Promise.resolve({
          ok: true,
          status: 204,
          json: async () => ({}),
        })
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) })
    })
    vi.stubGlobal('fetch', fetch)

    render(App)
    await new Promise((r) => setTimeout(r, 0))

    await fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument()
    })

    const logoutCalls = fetch.mock.calls.filter(
      (c: unknown[]) => (c[0] as string) === '/api/v1/auth/logout',
    )
    expect(logoutCalls).toHaveLength(2)

    const csrfCalls = fetch.mock.calls.filter(
      (c: unknown[]) => (c[0] as string) === '/api/v1/auth/csrf',
    )
    expect(csrfCalls.length).toBeGreaterThanOrEqual(2)
  })
})
