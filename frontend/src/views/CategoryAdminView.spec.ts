import { fireEvent, render, screen } from '@testing-library/vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createCategory, listCategories } from '../api/catalog'
import CategoryAdminView from './CategoryAdminView.vue'

vi.mock('../api/catalog', () => ({ listCategories: vi.fn(), createCategory: vi.fn(), archiveCategory: vi.fn() }))
const list = vi.mocked(listCategories); const create = vi.mocked(createCategory)
beforeEach(() => { list.mockResolvedValue([{ id:'p', parentId:null, name:'食品', defaultInventoryType:'BATCH', attributeSchema:[], version:0, archived:false }]); create.mockResolvedValue({ id:'c', parentId:'p', name:'乳制品', defaultInventoryType:'BATCH', attributeSchema:[], version:0, archived:false }) })

describe('CategoryAdminView', () => {
  it('展示树并添加子分类', async () => {
    render(CategoryAdminView)
    expect(await screen.findByText('食品')).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '添加子分类' }))
    await fireEvent.update(screen.getByLabelText('分类名称'), '乳制品')
    await fireEvent.click(screen.getByRole('button', { name: '保存分类' }))
    expect(create).toHaveBeenCalledWith(expect.objectContaining({ name: '乳制品', parentId: 'p' }))
  })
})
