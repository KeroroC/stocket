package com.stocket.inventory.internal.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class IdempotentExecutor {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RequestHasher requestHasher;

    public IdempotentExecutor(JdbcTemplate jdbc, ObjectMapper objectMapper, RequestHasher requestHasher) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.requestHasher = requestHasher;
    }

    public <T> Result<T> execute(Context context, String operation, String key,
                                 Object request, Class<T> responseType, Work<T> work) {
        String hash = requestHasher.hash(context.accountId(), operation, request);
        UUID recordId = UUID.randomUUID();
        Instant now = Instant.now();
        List<UUID> inserted = jdbc.query("""
                insert into idempotency_record(
                    id, household_id, account_id, operation, idempotency_key, request_hash,
                    status, created_at, expires_at
                ) values (?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?)
                on conflict (account_id, operation, idempotency_key) do nothing
                returning id
                """, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                recordId, context.householdId(), context.accountId(), operation, key, hash,
                Timestamp.from(now), Timestamp.from(now.plus(RETENTION)));

        if (inserted.isEmpty()) {
            return replay(context.accountId(), operation, key, hash, responseType);
        }

        Result<T> result = work.execute(recordId);
        try {
            String responseJson = objectMapper.writeValueAsString(result.body());
            int changed = jdbc.update("""
                    update idempotency_record
                    set status='COMPLETED', http_status=?, response_body=?::jsonb
                    where id=? and status='PROCESSING'
                    """, result.httpStatus(), responseJson, recordId);
            if (changed != 1) {
                throw new IllegalStateException("Idempotency record completion failed");
            }
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize idempotent response", exception);
        }
    }

    private <T> Result<T> replay(UUID accountId, String operation, String key,
                                 String hash, Class<T> responseType) {
        StoredRecord stored = jdbc.queryForObject("""
                select request_hash, status, http_status, response_body::text
                from idempotency_record
                where account_id=? and operation=? and idempotency_key=?
                """, (resultSet, rowNumber) -> new StoredRecord(
                        resultSet.getString("request_hash"), resultSet.getString("status"),
                        (Integer) resultSet.getObject("http_status"), resultSet.getString("response_body")),
                accountId, operation, key);
        if (!stored.requestHash().equals(hash)) {
            throw new IdempotencyException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
        }
        if (!"COMPLETED".equals(stored.status())) {
            throw new IdempotencyException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS");
        }
        try {
            return new Result<>(stored.httpStatus(), objectMapper.readValue(stored.responseBody(), responseType));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to replay idempotent response", exception);
        }
    }

    public record Context(UUID householdId, UUID accountId) {
    }

    public record Result<T>(int httpStatus, T body) {
    }

    @FunctionalInterface
    public interface Work<T> {
        Result<T> execute(UUID idempotencyRecordId);
    }

    public static class IdempotencyException extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        public IdempotencyException(HttpStatus status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }

        public HttpStatus status() {
            return status;
        }

        public String code() {
            return code;
        }
    }

    private record StoredRecord(String requestHash, String status, Integer httpStatus, String responseBody) {
    }
}
