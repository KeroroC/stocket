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
  it('默认显示全部物品并在清空搜索后恢复全部列表', async () => {
    vi.useFakeTimers()
    vi.mocked(searchCatalog)
      .mockResolvedValueOnce({ items:[{id:'1',name:'牛奶',categoryPath:'食品',brand:null,model:null,specification:null,tags:[],barcodes:[],matchType:'TEXT'}],page:0,size:20,total:1 })
      .mockResolvedValueOnce({ items:[],page:0,size:20,total:0 })
      .mockResolvedValueOnce({ items:[{id:'1',name:'牛奶',categoryPath:'食品',brand:null,model:null,specification:null,tags:[],barcodes:[],matchType:'TEXT'}],page:0,size:20,total:1 })

    render(ItemsView, { props: { role: 'MEMBER' } })
    await vi.runAllTimersAsync()
    expect(await screen.findByText('牛奶')).toBeInTheDocument()

    const input = screen.getByRole('searchbox', { name: '搜索物品' })
    await fireEvent.update(input, '不存在')
    await vi.advanceTimersByTimeAsync(250)
    expect(await screen.findByText('没有找到物品')).toBeInTheDocument()
    await fireEvent.update(input, '')
    await vi.runAllTimersAsync()
    expect(await screen.findByText('牛奶')).toBeInTheDocument()
  })

  it('搜索并标记条码精确结果', async () => {
    vi.useFakeTimers()
    vi.mocked(searchCatalog)
      .mockResolvedValueOnce({ items:[],page:0,size:20,total:0 })
      .mockResolvedValue({ items:[{id:'1',name:'牛奶',categoryPath:'食品 / 乳制品',brand:'品牌',model:null,specification:'250ml',tags:['早餐'],barcodes:['ABC'],matchType:'BARCODE_EXACT'}],page:0,size:20,total:1 })
    render(ItemsView, { props: { role: 'MEMBER' } })
    await vi.runAllTimersAsync()
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
    await fireEvent.click(screen.getByRole('button', { name: '展开分类浏览' }))
    expect(screen.getByRole('radio', { name: '按分类' })).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('radio', { name: '按位置' }))
    expect(await screen.findByText('冰箱')).toBeInTheDocument()
  })

  it('创建状态提供明确的返回目录操作', async () => {
    render(ItemsView, { props: { role: 'MEMBER' } })
    await fireEvent.click(screen.getByRole('button', { name: '创建物品' }))
    expect(screen.getByRole('heading', { name: '创建目录条目' })).toBeInTheDocument()
    await fireEvent.click(screen.getByRole('button', { name: '返回物品列表' }))
    expect(screen.getByRole('heading', { name: '全部物品' })).toBeInTheDocument()
  })
})
