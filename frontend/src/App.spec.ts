import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it } from 'vitest'
import App from './App.vue'

afterEach(cleanup)

describe('App', () => {
  it('展示家庭资产工程基础状态', () => {
    render(App)

    expect(
      screen.getByRole('heading', { name: '家庭资产' }),
    ).toBeInTheDocument()
    expect(screen.getByText('工程基础已就绪')).toBeInTheDocument()
  })

  it('为基础状态提供明确的样式挂钩', () => {
    render(App)

    const status = screen
      .getByText('Java 21 · Spring Boot Native · Vue 3')
      .closest('.foundation-status')

    expect(status).not.toBeNull()
  })
})
