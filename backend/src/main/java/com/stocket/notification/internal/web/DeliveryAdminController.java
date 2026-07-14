package com.stocket.notification.internal.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.stocket.identity.CurrentHouseholdProvider;

@RestController
@RequestMapping("/api/v1/admin/notification/deliveries")
class DeliveryAdminController {

    private static final List<String> STATUSES =
            List.of("PENDING", "PROCESSING", "DELIVERED", "RETRY_WAIT", "DEAD", "CANCELLED");

    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider currentHousehold;

    DeliveryAdminController(JdbcTemplate jdbc, CurrentHouseholdProvider currentHousehold) {
        this.jdbc = jdbc;
        this.currentHousehold = currentHousehold;
    }

    @GetMapping
    DeliveryPage list(@RequestParam(defaultValue = "DEAD") String status,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size) {
        String normalized = status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized) || page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        UUID householdId = currentHousehold.requireCurrent().householdId();
        long total = jdbc.queryForObject("""
                select count(*) from notification_delivery where household_id=? and status=?
                """, Long.class, householdId, normalized);
        List<DeliveryResponse> content = jdbc.query("""
                select id,reminder_id,member_id,channel_type,status,attempt_count,next_attempt_at,
                    last_error_code,last_error_at,delivered_at,updated_at
                from notification_delivery
                where household_id=? and status=?
                order by updated_at desc,id desc limit ? offset ?
                """, (result, row) -> response(result), householdId, normalized, size, (long) page * size);
        return new DeliveryPage(content, page, size, total);
    }

    @PostMapping("/{id}/retry")
    @Transactional
    DeliveryResponse retry(@PathVariable UUID id) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        int updated = jdbc.update("""
                update notification_delivery
                set status='PENDING',attempt_count=0,next_attempt_at=now(),last_error_code=null,
                    lease_owner=null,lease_until=null,updated_at=now()
                where id=? and household_id=? and status in ('DEAD','RETRY_WAIT')
                """, id, householdId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return jdbc.query("""
                select id,reminder_id,member_id,channel_type,status,attempt_count,next_attempt_at,
                    last_error_code,last_error_at,delivered_at,updated_at
                from notification_delivery where id=? and household_id=?
                """, (result, row) -> response(result), id, householdId).getFirst();
    }

    private DeliveryResponse response(java.sql.ResultSet result) throws java.sql.SQLException {
        return new DeliveryResponse(
                result.getObject("id", UUID.class), result.getObject("reminder_id", UUID.class),
                result.getObject("member_id", UUID.class), result.getString("channel_type"),
                result.getString("status"), result.getInt("attempt_count"),
                instant(result, "next_attempt_at"), result.getString("last_error_code"),
                instant(result, "last_error_at"), instant(result, "delivered_at"),
                instant(result, "updated_at"));
    }

    private java.time.Instant instant(java.sql.ResultSet result, String column) throws java.sql.SQLException {
        java.time.OffsetDateTime value = result.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    record DeliveryPage(List<DeliveryResponse> content, int page, int size, long total) {
    }
}
