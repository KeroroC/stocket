import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import { uploadAttachment } from '../../api/attachment'
import AttachmentUploader from './AttachmentUploader.vue'

vi.mock('../../api/attachment', () => ({ uploadAttachment: vi.fn() }))

describe('AttachmentUploader', () => {
  it('requires confirmation before replacing a cover', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(false))
    vi.mocked(uploadAttachment).mockResolvedValue({ id:'a',ownerType:'ITEM_DEFINITION',ownerId:'i',purpose:'COVER_IMAGE',filename:'cover.png',mediaType:'image/png',sizeBytes:3,status:'AVAILABLE',createdAt:'now' })
    render(AttachmentUploader, { props:{ownerType:'ITEM_DEFINITION',ownerId:'i',purpose:'COVER_IMAGE',label:'替换封面'} })
    const input=screen.getByLabelText('替换封面')
    await fireEvent.change(input,{target:{files:[new File(['png'],'cover.png',{type:'image/png'})]}})
    await fireEvent.click(screen.getByRole('button',{name:'开始上传'}))
    expect(uploadAttachment).not.toHaveBeenCalled()
  })
})
