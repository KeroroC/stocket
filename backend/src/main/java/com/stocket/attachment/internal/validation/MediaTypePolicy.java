package com.stocket.attachment.internal.validation;

import java.util.Set;

public final class MediaTypePolicy {
    public static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private MediaTypePolicy() {}
}
