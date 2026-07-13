package com.stocket.inventory.internal.reconciliation;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReconciliationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    ReconciliationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<EntrySnapshot> findBatch(UUID householdId, UUID afterEntryId, int size) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("householdId", householdId)
                .addValue("afterEntryId", afterEntryId)
                .addValue("limit", size);
        String cursor = afterEntryId == null ? "" : " and e.id > :afterEntryId\n";
        return jdbc.query("""
                select e.id, e.available_quantity as actual_quantity,
                       coalesce(sum(m.quantity_delta), 0) as expected_quantity
                from inventory_entry e
                left join inventory_movement m on m.entry_id = e.id and m.household_id = e.household_id
                where e.household_id = :householdId
                """ + cursor + """
                group by e.id, e.available_quantity
                order by e.id
                limit :limit
                """, parameters, (resultSet, row) -> new EntrySnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getBigDecimal("expected_quantity"),
                resultSet.getBigDecimal("actual_quantity")));
    }

    boolean recordMismatch(UUID householdId, EntrySnapshot snapshot, Instant now) {
        MapSqlParameterSource parameters = issueParameters(householdId, snapshot, now);
        jdbc.update("""
                update inventory_reconciliation_issue
                set status='RESOLVED', resolved_at=:now
                where household_id=:householdId and entry_id=:entryId and status='OPEN'
                  and (expected_quantity <> :expectedQuantity or actual_quantity <> :actualQuantity)
                """, parameters);
        return jdbc.update("""
                insert into inventory_reconciliation_issue(
                    id, household_id, entry_id, expected_quantity, actual_quantity,
                    status, detected_at, resolved_at)
                values (:id, :householdId, :entryId, :expectedQuantity, :actualQuantity,
                        'OPEN', :now, null)
                on conflict do nothing
                """, parameters) == 1;
    }

    int resolve(UUID householdId, UUID entryId, Instant now) {
        return jdbc.update("""
                update inventory_reconciliation_issue
                set status='RESOLVED', resolved_at=:now
                where household_id=:householdId and entry_id=:entryId and status='OPEN'
                """, new MapSqlParameterSource()
                .addValue("householdId", householdId)
                .addValue("entryId", entryId)
                .addValue("now", timestamp(now), Types.TIMESTAMP_WITH_TIMEZONE));
    }

    private MapSqlParameterSource issueParameters(UUID householdId, EntrySnapshot snapshot, Instant now) {
        return new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("householdId", householdId)
                .addValue("entryId", snapshot.entryId())
                .addValue("expectedQuantity", snapshot.expectedQuantity())
                .addValue("actualQuantity", snapshot.actualQuantity())
                .addValue("now", timestamp(now), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    private OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    record EntrySnapshot(UUID entryId, BigDecimal expectedQuantity, BigDecimal actualQuantity) {
        boolean matches() {
            return expectedQuantity.compareTo(actualQuantity) == 0;
        }
    }
}
