import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import StStatusTag from '../StStatusTag.vue'

describe('StStatusTag', () => {
  it.each(['healthy', 'expiring', 'expired', 'low-stock', 'archived'] as const)('用文字表达 %s 状态', (status) => {
    render(StStatusTag, { props: { status, label: `状态-${status}` } })
    expect(screen.getByText(`状态-${status}`)).toHaveAttribute('data-status', status)
  })
})
