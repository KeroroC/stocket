package com.stocket.attachment.internal.config;

import com.stocket.attachment.AttachmentSummary;
import com.stocket.attachment.internal.validation.ValidatedUpload;
import com.stocket.audit.AuditEvent;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(AttachmentRuntimeHints.Hints.class)
public class AttachmentRuntimeHints {
    public static class Hints implements RuntimeHintsRegistrar {
        @Override public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (Class<?> type : new Class<?>[]{AttachmentSummary.class, ValidatedUpload.class, AuditEvent.class}) {
                hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS);
            }
            hints.resources().registerPattern("org/apache/tika/mime/*");
        }
    }
}
