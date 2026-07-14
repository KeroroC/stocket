import { render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import { getItem } from '../api/catalog'
import { listAttachments } from '../api/attachment'
import ItemDetailView from './ItemDetailView.vue'

vi.mock('../api/catalog', () => ({ getItem: vi.fn(), archiveItem: vi.fn(), updateItem: vi.fn() }))
vi.mock('../api/attachment', () => ({ listAttachments: vi.fn(), uploadAttachment: vi.fn(), deleteAttachment: vi.fn(), downloadAttachment: vi.fn() }))

describe('ItemDetailView', () => {
  it('展示条码标签且只读不显示写操作', async () => {
    vi.mocked(getItem).mockResolvedValue({id:'1',name:'牛奶',categoryId:'c',brand:null,model:null,specification:null,defaultUnit:'盒',defaultShelfLifeValue:null,defaultShelfLifeUnit:null,customAttributes:{},barcodes:['ABC'],tags:['早餐'],version:0,archived:false})
    vi.mocked(listAttachments).mockResolvedValue([{ id:'att-1',ownerType:'ITEM_DEFINITION',ownerId:'1',purpose:'ITEM_IMAGE',filename:'正面.png',mediaType:'image/png',sizeBytes:12,status:'AVAILABLE',createdAt:'2026-07-14T00:00:00Z' }])
    render(ItemDetailView, { props: { itemId:'1', role:'VIEWER' } })
    expect(await screen.findByRole('heading', { name:'牛奶' })).toBeInTheDocument()
    expect(screen.getByText('ABC')).toBeInTheDocument()
    expect(await screen.findByText('正面.png')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name:'归档物品' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name:/上传/ })).not.toBeInTheDocument()
  })
})
