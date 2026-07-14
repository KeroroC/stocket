package com.stocket.attachment.internal.validation;

public final class AttachmentValidationException extends RuntimeException {
    private final String code;
    public AttachmentValidationException(String code) { super(code); this.code = code; }
    public String code() { return code; }
}
