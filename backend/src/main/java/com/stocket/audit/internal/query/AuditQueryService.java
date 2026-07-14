package com.stocket.audit.internal.query;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.stocket.identity.CurrentHouseholdProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditQueryService {
    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() { };
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CurrentHouseholdProvider current;

    public AuditQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, CurrentHouseholdProvider current) {
        this.jdbc = jdbc; this.objectMapper = objectMapper; this.current = current;
    }

    @Transactional(readOnly = true)
    public AuditResponse search(Instant from, Instant to, UUID actorId, String eventType, String outcome,
                                String subjectType, UUID subjectId, String requestId, String cursorValue, int size) {
        if (size < 1 || size > 200 || (from != null && to != null && from.isAfter(to))) throw invalid();
        Instant effectiveTo = to == null ? Instant.now() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(30, ChronoUnit.DAYS) : from;
        Cursor cursor = decode(cursorValue);
        StringBuilder predicates = new StringBuilder("""
                where log.household_id=:householdId and log.occurred_at between :from and :to
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("householdId", current.requireCurrent().householdId())
                .addValue("from", java.sql.Timestamp.from(effectiveFrom))
                .addValue("to", java.sql.Timestamp.from(effectiveTo)).addValue("limit", size + 1);
        append(predicates, parameters, actorId, "actorId", " and log.actor_account_id=:actorId\n");
        append(predicates, parameters, clean(eventType), "eventType", " and log.event_type=:eventType\n");
        append(predicates, parameters, clean(outcome), "outcome", " and log.outcome=:outcome\n");
        append(predicates, parameters, clean(subjectType), "subjectType", " and log.subject_type=:subjectType\n");
        append(predicates, parameters, subjectId, "subjectId", " and log.subject_id=:subjectId\n");
        append(predicates, parameters, clean(requestId), "requestId", " and log.request_id=:requestId\n");
        if (cursor != null) {
            predicates.append(" and (log.occurred_at < :cursorTime or (log.occurred_at=:cursorTime and log.id < :cursorId))\n");
            parameters.addValue("cursorTime", java.sql.Timestamp.from(cursor.occurredAt())).addValue("cursorId", cursor.id());
        }
        List<AuditResponse.AuditEntry> rows = jdbc.query("""
                select log.id,log.occurred_at,log.event_type,log.outcome,log.actor_account_id,
                       actor.display_name actor_display_name,log.subject_type,log.subject_id,
                       log.request_id,log.source,log.details::text details
                from audit_log log left join user_account actor on actor.id=log.actor_account_id
                """ + predicates + " order by log.occurred_at desc,log.id desc limit :limit", parameters,
                (resultSet, row) -> new AuditResponse.AuditEntry(
                        resultSet.getObject("id", UUID.class), resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getString("event_type"), resultSet.getString("outcome"),
                        resultSet.getObject("actor_account_id", UUID.class), resultSet.getString("actor_display_name"),
                        resultSet.getString("subject_type"), resultSet.getObject("subject_id", UUID.class),
                        resultSet.getString("request_id"), resultSet.getString("source"), details(resultSet.getString("details"))));
        boolean more = rows.size() > size;
        List<AuditResponse.AuditEntry> items = more ? new ArrayList<>(rows.subList(0, size)) : rows;
        AuditResponse.AuditEntry last = more ? items.getLast() : null;
        return new AuditResponse(List.copyOf(items), last == null ? null : encode(last.occurredAt(), last.id()));
    }

    private Map<String, Object> details(String json) {
        try { return objectMapper.readValue(json, DETAILS); }
        catch (Exception error) { throw new IllegalStateException("Invalid audit details", error); }
    }
    private void append(StringBuilder sql, MapSqlParameterSource parameters, Object value, String name, String clause) {
        if (value != null) { sql.append(clause); parameters.addValue(name, value); }
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private String encode(Instant time, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
    private Cursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw invalid();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException error) { throw invalid(); }
    }
    private InvalidAuditQuery invalid() { return new InvalidAuditQuery(); }
    public static final class InvalidAuditQuery extends RuntimeException { }
    private record Cursor(Instant occurredAt, UUID id) { }
}
