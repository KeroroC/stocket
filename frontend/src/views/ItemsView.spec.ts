import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchCatalog } from '../api/catalog'
import { listLocations } from '../api/location'
import ItemsView from './ItemsView.vue'

vi.mock('../api/catalog', () => ({ searchCatalog: vi.fn(), listCategories: vi.fn().mockResolvedValue([]), createItem: vi.fn() }))
vi.mock('../api/location', () => ({ listLocations: vi.fn().mockResolvedValue([]) }))
vi.mock('./ItemDetailView.vue', () => ({ default: { template: '<div>详情</div>' } }))
afterEach(() => { cleanup(); vi.useRealTimers() })

describe('ItemsView', () => {
  it('搜索并标记条码精确结果', async () => {
    vi.useFakeTimers()
    vi.mocked(searchCatalog).mockResolvedValue({ items:[{id:'1',name:'牛奶',categoryPath:'食品 / 乳制品',brand:'品牌',model:null,specification:'250ml',tags:['早餐'],barcodes:['ABC'],matchType:'BARCODE_EXACT'}],page:0,size:20,total:1 })
    render(ItemsView, { props: { role: 'MEMBER' } })
    await fireEvent.update(screen.getByRole('searchbox', { name: '搜索物品' }), 'ABC')
    await vi.advanceTimersByTimeAsync(250)
    expect(await screen.findByText('牛奶')).toBeInTheDocument()
    expect(screen.getByText('条码精确匹配')).toBeInTheDocument()
  })

  it('只读角色不显示创建操作', () => {
    render(ItemsView, { props: { role: 'VIEWER' } })
    expect(screen.queryByRole('button', { name: '创建物品' })).not.toBeInTheDocument()
  })

  it('可以切换分类和位置浏览', async () => {
    vi.mocked(listLocations).mockResolvedValue([{ id: 'loc-1', parentId: null, name: '冰箱', fullPath: '冰箱', publicCode: 'FRIDGE', version: 0, archived: false }])
    render(ItemsView, { props: { role: 'VIEWER' } })
    expect(screen.getByRole('button', { name: '按分类' })).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '按位置' }))
    expect(await screen.findByText('冰箱')).toBeInTheDocument()
  })
})
