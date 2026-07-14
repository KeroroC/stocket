package com.stocket.attachment.internal.storage;

import java.io.IOException;
import java.io.InputStream;

public interface AttachmentStore {
    StoredObject stage(InputStream input) throws IOException;
    void commit(StoredObject object) throws IOException;
    InputStream open(String storageKey) throws IOException;
    void delete(String storageKey) throws IOException;
    boolean exists(String storageKey);
}
