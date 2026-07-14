package com.stocket.audit;

import java.util.Map;

import com.stocket.audit.internal.domain.AuditDetailsPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditDetailsPolicyTest {
    private final AuditDetailsPolicy policy = new AuditDetailsPolicy();

    @Test void rejectsSensitiveKeysAtAnyDepth() {
        for (String key : new String[]{"password", "accessToken", "client_secret", "requestBody", "authorization", "cookie"}) {
            assertThatThrownBy(() -> policy.sanitize("AttachmentUploaded", Map.of("ownerType", "ITEM_DEFINITION", "nested", Map.of(key, "value"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("AUDIT_DETAILS_SENSITIVE");
        }
    }

    @Test void keepsOnlyWhitelistedFieldsAndBoundsStrings() {
        Map<String, Object> sanitized = policy.sanitize("AttachmentUploaded", Map.of(
                "ownerType", "ITEM_DEFINITION", "purpose", "ITEM_IMAGE", "ignored", "value",
                "filename", "x".repeat(700)));
        assertThat(sanitized).containsEntry("ownerType", "ITEM_DEFINITION").containsEntry("purpose", "ITEM_IMAGE");
        assertThat(sanitized).doesNotContainKey("ignored");
        assertThat(sanitized.get("filename").toString()).hasSize(500);
    }
}
