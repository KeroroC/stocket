package com.stocket.attachment.internal.config;

import java.io.IOException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.stocket.attachment.internal.storage.LocalAttachmentStore;
import com.stocket.attachment.internal.validation.AttachmentValidator;

@Configuration
public class AttachmentConfiguration {
    @Bean LocalAttachmentStore localAttachmentStore(@Value("${stocket.attachment.directory}") String directory,
                                                     @Value("${stocket.attachment.max-size-bytes}") long max) throws IOException {
        return new LocalAttachmentStore(Path.of(directory), max);
    }
    @Bean AttachmentValidator attachmentValidator(@Value("${stocket.attachment.max-size-bytes}") long max) {
        return new AttachmentValidator(max, 40_000_000L);
    }
}
