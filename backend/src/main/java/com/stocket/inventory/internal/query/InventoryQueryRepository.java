package com.stocket.inventory.internal.query;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.stocket.inventory.InventoryItemAvailability;

@Repository
class InventoryQueryRepository {

    private static final TypeReference<Map<String, Object>> ATTRIBUTES_TYPE = new TypeReference<>() { };
    private static final String ENTRY_SELECT = """
            select e.id, e.item_definition_id, i.name as item_name,
                   e.location_id, l.name as location_name, e.inventory_type,
                   e.available_quantity, e.received_at, e.production_date, e.expiration_date,
                   e.custom_attributes::text as custom_attributes, e.version, e.archived_at,
                   b.batch_number, b.source_entry_id, b.shelf_life_value, b.shelf_life_unit,
                   a.asset_number, a.serial_number, a.purchase_date, a.warranty_expires_on, a.status as asset_status
            from inventory_entry e
            join item_definition i on i.id = e.item_definition_id
            join location l on l.id = e.location_id
            left join batch_detail b on b.inventory_entry_id = e.id
            left join asset_detail a on a.inventory_entry_id = e.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    InventoryQueryRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    EntryPage findEntries(UUID householdId, EntryFilter filter) {
        MapSqlParameterSource parameters = parameters(householdId, filter);
        String predicates = predicates(filter);
        Long total = jdbc.queryForObject("""
                select count(*)
                from inventory_entry e
                left join asset_detail a on a.inventory_entry_id = e.id
                where e.household_id = :householdId
                """ + predicates, parameters, Long.class);

        parameters.addValue("limit", filter.size()).addValue("offset", (long) filter.page() * filter.size());
        List<InventoryEntryResponse> items = jdbc.query(ENTRY_SELECT + """
                where e.household_id = :householdId
                """ + predicates + """
                order by e.expiration_date asc nulls last, e.received_at asc, e.id asc
                limit :limit offset :offset
                """, parameters, this::mapEntry);
        return new EntryPage(items, filter.page(), filter.size(), total == null ? 0 : total);
    }

    long countEntries(UUID householdId, EntryFilter filter) {
        Long total = jdbc.queryForObject("""
                select count(*)
                from inventory_entry e
                left join asset_detail a on a.inventory_entry_id = e.id
                where e.household_id = :householdId
                """ + predicates(filter), parameters(householdId, filter), Long.class);
        return total == null ? 0 : total;
    }

    List<InventoryEntryResponse> exportEntries(UUID householdId, EntryFilter filter, UUID afterId, int size) {
        MapSqlParameterSource parameters = parameters(householdId, filter)
                .addValue("afterId", afterId).addValue("limit", size);
        return jdbc.query(ENTRY_SELECT + """
                where e.household_id = :householdId
                """ + predicates(filter) + """
                  and (cast(:afterId as uuid) is null or e.id > :afterId)
                order by e.id
                limit :limit
                """, parameters, this::mapEntry);
    }

    Optional<InventoryEntryResponse> findEntry(UUID householdId, UUID entryId) {
        return jdbc.query(ENTRY_SELECT + """
                where e.household_id = :householdId
                  and e.id = :entryId
                  and e.archived_at is null
                """, Map.of("householdId", householdId, "entryId", entryId), this::mapEntry)
                .stream().findFirst();
    }

    List<MovementResponse> findMovements(UUID householdId, UUID entryId) {
        return jdbc.query("""
                select m.id, m.movement_type, m.quantity_delta, m.related_entry_id,
                       m.from_location_id, m.to_location_id, m.reason, m.actor_account_id,
                       u.display_name as actor_display_name, m.request_id, m.occurred_at
                from inventory_entry e
                join inventory_movement m on m.entry_id = e.id
                join user_account u on u.id = m.actor_account_id
                where e.household_id = :householdId
                  and e.id = :entryId
                  and e.archived_at is null
                order by m.occurred_at desc, m.id desc
                """, Map.of("householdId", householdId, "entryId", entryId), (resultSet, row) ->
                new MovementResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("movement_type"),
                        decimal(resultSet.getBigDecimal("quantity_delta")),
                        resultSet.getObject("related_entry_id", UUID.class),
                        resultSet.getObject("from_location_id", UUID.class),
                        resultSet.getObject("to_location_id", UUID.class),
                        resultSet.getString("reason"),
                        resultSet.getObject("actor_account_id", UUID.class),
                        resultSet.getString("actor_display_name"),
                        resultSet.getString("request_id"),
                        resultSet.getTimestamp("occurred_at").toInstant()));
    }

    Optional<InventoryItemAvailability> availability(UUID householdId, UUID itemId) {
        return jdbc.query("""
                select e.item_definition_id,
                       coalesce(sum(e.available_quantity), 0) as total_available,
                       min(e.expiration_date) filter (where e.available_quantity > 0) as earliest_expiration,
                       count(*) filter (where e.available_quantity > 0) as active_entry_count
                from inventory_entry e
                where e.household_id = :householdId
                  and e.item_definition_id = :itemId
                  and e.archived_at is null
                group by e.item_definition_id
                """, Map.of("householdId", householdId, "itemId", itemId), (resultSet, row) ->
                new InventoryItemAvailability(
                        resultSet.getObject("item_definition_id", UUID.class),
                        resultSet.getBigDecimal("total_available"),
                        resultSet.getObject("earliest_expiration", LocalDate.class),
                        resultSet.getInt("active_entry_count")))
                .stream().findFirst();
    }

    private String predicates(EntryFilter filter) {
        StringBuilder sql = new StringBuilder();
        if (!filter.includeArchived()) sql.append(" and e.archived_at is null\n");
        if (filter.itemId() != null) sql.append(" and e.item_definition_id = :itemId\n");
        if (filter.locationId() != null) sql.append(" and e.location_id = :locationId\n");
        if (filter.type() != null) sql.append(" and e.inventory_type = :type\n");
        if (filter.assetStatus() != null) sql.append(" and a.status = :assetStatus\n");
        if (filter.expiresFrom() != null) sql.append(" and e.expiration_date >= :expiresFrom\n");
        if (filter.expiresTo() != null) sql.append(" and e.expiration_date <= :expiresTo\n");
        return sql.toString();
    }

    private MapSqlParameterSource parameters(UUID householdId, EntryFilter filter) {
        return new MapSqlParameterSource()
                .addValue("householdId", householdId)
                .addValue("itemId", filter.itemId())
                .addValue("locationId", filter.locationId())
                .addValue("type", filter.type())
                .addValue("assetStatus", filter.assetStatus())
                .addValue("expiresFrom", filter.expiresFrom())
                .addValue("expiresTo", filter.expiresTo());
    }

    private InventoryEntryResponse mapEntry(ResultSet resultSet, int row) throws SQLException {
        return new InventoryEntryResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("item_definition_id", UUID.class),
                resultSet.getString("item_name"),
                resultSet.getObject("location_id", UUID.class),
                resultSet.getString("location_name"),
                resultSet.getString("inventory_type"),
                decimal(resultSet.getBigDecimal("available_quantity")),
                resultSet.getTimestamp("received_at").toInstant(),
                resultSet.getObject("production_date", LocalDate.class),
                resultSet.getObject("expiration_date", LocalDate.class),
                attributes(resultSet.getString("custom_attributes")),
                resultSet.getLong("version"),
                resultSet.getTimestamp("archived_at") != null,
                resultSet.getString("batch_number"),
                resultSet.getObject("source_entry_id", UUID.class),
                resultSet.getObject("shelf_life_value", Integer.class),
                resultSet.getString("shelf_life_unit"),
                resultSet.getString("asset_number"),
                resultSet.getString("serial_number"),
                resultSet.getObject("purchase_date", LocalDate.class),
                resultSet.getObject("warranty_expires_on", LocalDate.class),
                resultSet.getString("asset_status"));
    }

    private Map<String, Object> attributes(String json) {
        try {
            return objectMapper.readValue(json, ATTRIBUTES_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid inventory custom attributes", exception);
        }
    }

    private static String decimal(BigDecimal value) {
        if (value.signum() == 0) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    record EntryFilter(UUID itemId, UUID locationId, String type, String assetStatus,
                       LocalDate expiresFrom, LocalDate expiresTo,
                       boolean includeArchived, int page, int size) { }

    record EntryPage(List<InventoryEntryResponse> items, int page, int size, long total) { }
}
