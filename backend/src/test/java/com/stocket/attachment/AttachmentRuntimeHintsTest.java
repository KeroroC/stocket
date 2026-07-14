package com.stocket.attachment;

import com.stocket.attachment.internal.config.AttachmentRuntimeHints;
import com.stocket.attachment.internal.validation.ValidatedUpload;
import com.stocket.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentRuntimeHintsTest {
    @Test void registersAttachmentAuditPayloadsAndTikaMimeResources() {
        RuntimeHints hints = new RuntimeHints();
        new AttachmentRuntimeHints.Hints().registerHints(hints, getClass().getClassLoader());
        assertThat(RuntimeHintsPredicates.reflection().onType(AttachmentSummary.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(ValidatedUpload.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(AuditEvent.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.resource().forResource("org/apache/tika/mime/tika-mimetypes.xml").test(hints)).isTrue();
    }
}
