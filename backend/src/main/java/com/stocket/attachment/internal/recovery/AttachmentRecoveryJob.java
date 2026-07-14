package com.stocket.attachment.internal.recovery;

import java.io.IOException;
import com.stocket.attachment.internal.domain.*;
import com.stocket.attachment.internal.storage.LocalAttachmentStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AttachmentRecoveryJob {
    private final AttachmentRepository repository; private final LocalAttachmentStore store;
    public AttachmentRecoveryJob(AttachmentRepository repository, LocalAttachmentStore store){this.repository=repository;this.store=store;}
    @Transactional public void recover() throws IOException {
        for(Attachment attachment:repository.findByStatus(AttachmentStatus.STAGED)){
            if(store.stagedExists(attachment.getStorageKey())){store.commitStaged(attachment.getStorageKey());attachment.available();}
            else if(store.exists(attachment.getStorageKey())) attachment.available(); else attachment.missing();
        }
        for(Attachment attachment:repository.findByStatus(AttachmentStatus.AVAILABLE)) if(!store.exists(attachment.getStorageKey())) attachment.missing();
        for(Attachment attachment:repository.findByStatus(AttachmentStatus.DELETED)) store.delete(attachment.getStorageKey());
    }
}
