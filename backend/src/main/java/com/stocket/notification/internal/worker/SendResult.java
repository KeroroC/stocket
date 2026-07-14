package com.stocket.notification.internal.worker;

import java.time.Duration;
import java.util.Optional;

public record SendResult(String outcome, String errorCode, Optional<Duration> retryAfter) {

    public static SendResult delivered() {
        return new SendResult("DELIVERED", null, Optional.empty());
    }

    public static SendResult retry(String code, Duration retryAfter) {
        return new SendResult("RETRY", code, Optional.ofNullable(retryAfter));
    }

    public static SendResult permanent(String code) {
        return new SendResult("PERMANENT_FAILURE", code, Optional.empty());
    }

    public static SendResult fromHttp(int status, Duration retryAfter) {
        if (status >= 200 && status < 300) return delivered();
        if (status == 408 || status == 429 || status >= 500) {
            return retry("HTTP_" + status, status == 429 ? retryAfter : null);
        }
        return permanent("HTTP_" + status);
    }
}
