import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import StPageHeader from '../StPageHeader.vue'

describe('StPageHeader', () => {
  it('展示标题说明和操作', () => {
    render(StPageHeader, {
      props: { title: '物品目录', description: '管理家庭物品', eyebrow: '目录' },
      slots: { actions: '<button>添加物品</button>' },
    })
    expect(screen.getByRole('heading', { name: '物品目录' })).toBeInTheDocument()
    expect(screen.getByText('管理家庭物品')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '添加物品' })).toBeInTheDocument()
  })
})
