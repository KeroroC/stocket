import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import { defineComponent, h } from 'vue'
import { uploadAttachment } from '../../api/attachment'
import AttachmentUploader from './AttachmentUploader.vue'

vi.mock('../../api/attachment', () => ({ uploadAttachment: vi.fn() }))

describe('AttachmentUploader', () => {
  it('requires confirmation before replacing a cover', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce('cancel')
    vi.mocked(uploadAttachment).mockResolvedValue({ id:'a',ownerType:'ITEM_DEFINITION',ownerId:'i',purpose:'COVER_IMAGE',filename:'cover.png',mediaType:'image/png',sizeBytes:3,status:'AVAILABLE',createdAt:'now' })
    const file = new File(['png'],'cover.png',{type:'image/png'})
    const ElUploadStub = defineComponent({
      props:{ onChange:Function },
      setup(props) { return () => h('button', { onClick:() => props.onChange?.({ raw:file }) }, '选择文件') },
    })
    render(AttachmentUploader, {
      props:{ownerType:'ITEM_DEFINITION',ownerId:'i',purpose:'COVER_IMAGE',label:'替换封面'},
      global:{ stubs:{ ElUpload:ElUploadStub } },
    })
    await fireEvent.click(screen.getByRole('button',{name:'选择文件'}))
    await fireEvent.click(screen.getByRole('button',{name:'开始上传'}))
    expect(uploadAttachment).not.toHaveBeenCalled()
  })
})
