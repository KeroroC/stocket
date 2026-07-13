import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import StFormActions from '../StFormActions.vue'

describe('StFormActions', () => {
  it('提交中禁用主操作', async () => {
    const { emitted } = render(StFormActions, { props: { primaryLabel: '保存', pending: true } })
    const button = screen.getByRole('button', { name: '正在保存' })
    expect(button).toBeDisabled()
    await fireEvent.click(button)
    expect(emitted().submit).toBeUndefined()
  })
})
