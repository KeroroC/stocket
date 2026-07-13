import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchCatalog } from '../api/catalog'
import ItemsView from './ItemsView.vue'

vi.mock('../api/catalog', () => ({ searchCatalog: vi.fn(), listCategories: vi.fn().mockResolvedValue([]), createItem: vi.fn() }))
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
})
