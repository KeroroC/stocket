package com.stocket.system.diagnostics;

import java.time.Instant;
import java.util.Map;

public record DiagnosticsResponse(Map<String, Check> checks) {
    public record Check(String status, long count, Instant checkedAt, String actionCode) { }
}
