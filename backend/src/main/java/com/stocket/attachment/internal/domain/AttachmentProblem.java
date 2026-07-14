package com.stocket.attachment.internal.domain;

import org.springframework.http.HttpStatus;

public final class AttachmentProblem extends RuntimeException {
    private final HttpStatus status; private final String code;
    public AttachmentProblem(HttpStatus status, String code) { super(code); this.status=status; this.code=code; }
    public HttpStatus status(){return status;} public String code(){return code;}
}
