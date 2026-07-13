import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import StEmptyState from '../StEmptyState.vue'

describe('StEmptyState', () => {
  it('展示说明和单一操作', () => {
    render(StEmptyState, { props: { title: '还没有物品', description: '先创建一个档案' }, slots: { default: '<button>添加第一个物品</button>' } })
    expect(screen.getByRole('heading', { name: '还没有物品' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '添加第一个物品' })).toBeInTheDocument()
  })
})
