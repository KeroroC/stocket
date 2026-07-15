import { fireEvent, render, screen } from '@testing-library/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createLocation, listLocations } from '../api/location'
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
    expect(screen.getByText(/系统在创建位置时自动生成/)).toBeInTheDocument()
  })

  it('空位置时可以创建第一个顶级位置', async () => {
    vi.mocked(listLocations).mockResolvedValueOnce([])
    vi.mocked(createLocation).mockResolvedValueOnce({ id:'home', parentId:null, name:'家', fullPath:'家', publicCode:'generated', version:0, archived:false })
    render(LocationAdminView)

    await fireEvent.click(await screen.findByRole('button', { name: '创建第一个位置' }))
    await fireEvent.update(screen.getByLabelText('位置名称'), '家')
    await fireEvent.click(screen.getByRole('button', { name: '保存位置' }))

    expect(createLocation).toHaveBeenCalledWith({ name: '家', parentId: null })
  })
})
