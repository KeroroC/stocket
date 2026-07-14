package com.stocket.attachment;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.stocket.attachment.internal.storage.LocalAttachmentStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAttachmentStoreTest {

    @TempDir Path temp;

    @Test
    void usesRandomTwoLevelKeyAndNeverUsesClientFilenameInPath() throws Exception {
        LocalAttachmentStore store = new LocalAttachmentStore(temp, 1024);
        var staged = store.stage(new ByteArrayInputStream("payload".getBytes()));

        assertThat(staged.storageKey()).matches("[0-9a-f]{64}");
        assertThat(staged.stagingPath().normalize()).startsWith(temp.toRealPath());
        store.commit(staged);
        Path expected = temp.resolve(staged.storageKey().substring(0, 2))
                .resolve(staged.storageKey().substring(2, 4)).resolve(staged.storageKey());
        assertThat(expected).exists();
        assertThat(store.open(staged.storageKey())).hasContent("payload");
        for (String hostile : new String[]{"../secret", "/etc/passwd", "a\0b", "..\\secret", "a∕b"}) {
            assertThat(LocalAttachmentStore.sanitizeFilename(hostile)).doesNotContain("/", "\\", "\0");
        }
    }

    @Test
    void limitsStreamingBytesAndRejectsSymlinkRoot() throws Exception {
        LocalAttachmentStore store = new LocalAttachmentStore(temp.resolve("safe"), 4);
        assertThatThrownBy(() -> store.stage(new ByteArrayInputStream("12345".getBytes())))
                .hasMessageContaining("ATTACHMENT_TOO_LARGE");

        Path real = Files.createDirectory(temp.resolve("real"));
        Path link = temp.resolve("link");
        Files.createSymbolicLink(link, real);
        assertThatThrownBy(() -> new LocalAttachmentStore(link, 100))
                .hasMessageContaining("ATTACHMENT_STORAGE_SYMLINK");

        Files.createDirectory(real.resolve("existing-child"));
        assertThatThrownBy(() -> new LocalAttachmentStore(link.resolve("existing-child"), 100))
                .hasMessageContaining("ATTACHMENT_STORAGE_SYMLINK");
    }
}
