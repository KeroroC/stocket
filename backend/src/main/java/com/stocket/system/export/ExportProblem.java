package com.stocket.system.export;

import org.springframework.http.HttpStatus;

final class ExportProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    ExportProblem(HttpStatus status, String code) { super(code); this.status = status; this.code = code; }
    HttpStatus status() { return status; }
    String code() { return code; }
}
