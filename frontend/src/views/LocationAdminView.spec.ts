import { fireEvent, render, screen } from '@testing-library/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listLocations } from '../api/location'
import LocationAdminView from './LocationAdminView.vue'

vi.mock('../api/location', () => ({ listLocations: vi.fn(), createLocation: vi.fn(), archiveLocation: vi.fn() }))
beforeEach(() => vi.mocked(listLocations).mockResolvedValue([{ id:'l', parentId:null, name:'冰箱', fullPath:'家 > 厨房 > 冰箱', publicCode:'code', version:0, archived:false }]))

describe('LocationAdminView', () => {
  it('展示完整路径并复制二维码文本', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
    render(LocationAdminView)
    expect((await screen.findAllByText('家 > 厨房 > 冰箱')).length).toBeGreaterThan(0)
    await fireEvent.click(screen.getByRole('button', { name: '复制位置码' }))
    expect(writeText).toHaveBeenCalledWith('stocket:location:code')
  })
})
