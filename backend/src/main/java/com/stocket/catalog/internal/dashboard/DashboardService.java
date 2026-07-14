package com.stocket.catalog.internal.dashboard;

import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.CurrentHouseholdProvider;

@Service
class DashboardService {

    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider currentHousehold;

    DashboardService(JdbcTemplate jdbc, CurrentHouseholdProvider currentHousehold) {
        this.jdbc = jdbc;
        this.currentHousehold = currentHousehold;
    }

    @Transactional(readOnly = true)
    DashboardResponse load(String query) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        DashboardSummary summary = jdbc.queryForObject("""
                select count(*) filter (where reminder_type='EXPIRING' and status in ('OPEN','ACKNOWLEDGED')),
                       count(*) filter (where reminder_type='EXPIRED' and status in ('OPEN','ACKNOWLEDGED')),
                       count(*) filter (where reminder_type='LOW_STOCK' and status in ('OPEN','ACKNOWLEDGED')),
                       count(*) filter (where status in ('OPEN','ACKNOWLEDGED'))
                from reminder where household_id=?
                """, (result, row) -> new DashboardSummary(
                result.getLong(1), result.getLong(2), result.getLong(3), result.getLong(4)), householdId);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<DashboardSearchItem> search = normalized.isBlank() ? List.of() : jdbc.query("""
                select item.id,item.name,
                       case when exists(select 1 from item_barcode barcode
                           where barcode.item_definition_id=item.id and barcode.archived_at is null
                             and barcode.normalized_value=?) then 'BARCODE_EXACT' else 'TEXT' end match_type,
                       coalesce(sum(entry.available_quantity)
                           filter (where entry.archived_at is null),0) total_available,
                       array_agg(distinct location.name)
                           filter (where entry.archived_at is null and location.name is not null) locations,
                       min(entry.expiration_date)
                           filter (where entry.archived_at is null and entry.available_quantity>0) earliest_expiration,
                       (select batch.batch_number from inventory_entry recent_entry
                           join batch_detail batch on batch.inventory_entry_id=recent_entry.id
                           where recent_entry.household_id=item.household_id
                             and recent_entry.item_definition_id=item.id
                             and recent_entry.archived_at is null
                           order by recent_entry.received_at desc,recent_entry.id desc limit 1) recent_batch
                from item_definition item
                left join inventory_entry entry on entry.item_definition_id=item.id
                    and entry.household_id=item.household_id
                left join location on location.id=entry.location_id
                where item.household_id=? and item.archived_at is null
                  and (item.normalized_name like ? or exists(select 1 from item_barcode barcode
                      where barcode.item_definition_id=item.id and barcode.archived_at is null
                        and barcode.normalized_value=?))
                group by item.id,item.name,item.household_id
                order by match_type,item.name,item.id limit 20
                """, (result, row) -> new DashboardSearchItem(
                result.getObject("id", UUID.class), result.getString("name"), result.getString("match_type"),
                decimal(result.getBigDecimal("total_available")), strings(result.getArray("locations")),
                result.getObject("earliest_expiration", LocalDate.class), result.getString("recent_batch")),
                normalized, householdId, "%" + normalized + "%", normalized);
        return new DashboardResponse(summary, search);
    }

    private List<String> strings(Array array) throws SQLException {
        if (array == null) return List.of();
        Object value = array.getArray();
        return value instanceof String[] strings ? Arrays.asList(strings) : List.of();
    }

    private String decimal(java.math.BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    record DashboardResponse(DashboardSummary summary, List<DashboardSearchItem> search) { }
    record DashboardSummary(long expiring, long expired, long lowStock, long openTotal) { }
    record DashboardSearchItem(UUID id, String name, String matchType, String totalAvailable,
                               List<String> locations, LocalDate earliestExpiration, String recentBatch) { }
}
