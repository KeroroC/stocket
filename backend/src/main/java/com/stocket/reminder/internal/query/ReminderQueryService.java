package com.stocket.reminder.internal.query;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stocket.identity.CurrentHouseholdProvider;

@Service
public class ReminderQueryService {

    private static final List<String> STATUSES =
            List.of("SCHEDULED", "OPEN", "ACKNOWLEDGED", "RESOLVED");
    private static final List<String> TYPES =
            List.of("EXPIRING", "EXPIRED", "LOW_STOCK", "INTEGRITY");

    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider currentHousehold;

    ReminderQueryService(JdbcTemplate jdbc, CurrentHouseholdProvider currentHousehold) {
        this.jdbc = jdbc;
        this.currentHousehold = currentHousehold;
    }

    public ReminderPage list(String status, String type, Instant from, Instant to, int page, int size) {
        String normalizedStatus = normalize(status, STATUSES);
        String normalizedType = normalize(type, TYPES);
        if (page < 0 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        UUID householdId = currentHousehold.requireCurrent().householdId();
        Filter filter = filter(householdId, normalizedStatus, normalizedType, from, to);
        long total = jdbc.queryForObject("select count(*) " + filter.fromAndWhere(),
                Long.class, filter.arguments().toArray());
        List<Object> pageArguments = new ArrayList<>(filter.arguments());
        pageArguments.add(size);
        pageArguments.add((long) page * size);
        List<ReminderResponse> content = jdbc.query("""
                select reminder.id, reminder.item_definition_id, reminder.inventory_entry_id,
                       item.name as item_name, location.name as location_name,
                       entry.available_quantity, reminder.reminder_type, reminder.trigger_key,
                       reminder.trigger_at, reminder.status, reminder.version
                """ + filter.fromAndWhere() + " order by reminder.trigger_at desc, reminder.id desc limit ? offset ?",
                (result, row) -> new ReminderResponse(
                        result.getObject("id", UUID.class),
                        result.getObject("item_definition_id", UUID.class),
                        result.getObject("inventory_entry_id", UUID.class),
                        result.getString("item_name"),
                        result.getString("location_name"),
                        decimal(result.getBigDecimal("available_quantity")),
                        result.getString("reminder_type"),
                        result.getString("trigger_key"),
                        result.getObject("trigger_at", java.time.OffsetDateTime.class).toInstant(),
                        result.getString("status"),
                        result.getLong("version")),
                pageArguments.toArray());
        return new ReminderPage(content, page, size, total);
    }

    @Transactional
    public ReminderResponse acknowledge(UUID reminderId) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        int updated = jdbc.update("""
                update reminder
                set status='ACKNOWLEDGED', updated_at=now(), version=version+1
                where id=? and household_id=? and status='OPEN'
                """, reminderId, householdId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return find(householdId, reminderId);
    }

    private ReminderResponse find(UUID householdId, UUID reminderId) {
        return jdbc.query("""
                select reminder.id, reminder.item_definition_id, reminder.inventory_entry_id,
                       item.name as item_name, location.name as location_name,
                       entry.available_quantity, reminder.reminder_type, reminder.trigger_key,
                       reminder.trigger_at, reminder.status, reminder.version
                from reminder
                join item_definition item on item.id=reminder.item_definition_id
                left join inventory_entry entry on entry.id=reminder.inventory_entry_id
                left join location on location.id=entry.location_id
                where reminder.household_id=? and reminder.id=?
                """, (result, row) -> new ReminderResponse(
                result.getObject("id", UUID.class), result.getObject("item_definition_id", UUID.class),
                result.getObject("inventory_entry_id", UUID.class), result.getString("item_name"),
                result.getString("location_name"), decimal(result.getBigDecimal("available_quantity")),
                result.getString("reminder_type"), result.getString("trigger_key"),
                result.getObject("trigger_at", java.time.OffsetDateTime.class).toInstant(),
                result.getString("status"), result.getLong("version")), householdId, reminderId).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Filter filter(UUID householdId, String status, String type, Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("""
                from reminder
                join item_definition item on item.id=reminder.item_definition_id
                left join inventory_entry entry on entry.id=reminder.inventory_entry_id
                left join location on location.id=entry.location_id
                where reminder.household_id=?
                """);
        List<Object> arguments = new ArrayList<>();
        arguments.add(householdId);
        if (status != null) {
            sql.append(" and reminder.status=?");
            arguments.add(status);
        }
        if (type != null) {
            sql.append(" and reminder.reminder_type=?");
            arguments.add(type);
        }
        if (from != null) {
            sql.append(" and reminder.trigger_at>=?");
            arguments.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and reminder.trigger_at<=?");
            arguments.add(Timestamp.from(to));
        }
        return new Filter(sql.toString(), arguments);
    }

    private String normalize(String value, List<String> allowed) {
        if (value == null) return null;
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return normalized;
    }

    private String decimal(java.math.BigDecimal value) {
        if (value == null) return null;
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private record Filter(String fromAndWhere, List<Object> arguments) {
    }

    public record ReminderPage(List<ReminderResponse> content, int page, int size, long total) {
    }
}
