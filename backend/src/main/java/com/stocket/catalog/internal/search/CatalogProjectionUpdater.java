package com.stocket.catalog.internal.search;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stocket.catalog.CatalogItemChanged;

@Component
class CatalogProjectionUpdater {
    private final JdbcTemplate jdbc;
    CatalogProjectionUpdater(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void update(CatalogItemChanged event) {
        List<String> tags = jdbc.queryForList(
                "select value from item_tag where item_definition_id = ? order by normalized_value",
                String.class, event.itemId());
        List<String> rawBarcodes = jdbc.queryForList(
                "select raw_value from item_barcode where item_definition_id = ? order by normalized_value",
                String.class, event.itemId());
        List<String> normalizedBarcodes = jdbc.queryForList(
                "select normalized_value from item_barcode where item_definition_id = ? order by normalized_value",
                String.class, event.itemId());
        List<Map<String, Object>> items = jdbc.queryForList("""
                with recursive category_ancestors as (
                    select c.id, c.household_id, c.parent_id, c.name, 0 as depth
                    from category c
                    join item_definition item on item.category_id = c.id
                    where item.household_id = ? and item.id = ?
                    union all
                    select parent.id, parent.household_id, parent.parent_id, parent.name, child.depth + 1
                    from category parent
                    join category_ancestors child on child.parent_id = parent.id
                    where parent.household_id = child.household_id
                )
                select i.name, i.brand, i.model, i.specification, i.archived_at is not null as archived,
                       (select string_agg(name, ' / ' order by depth desc) from category_ancestors) as category_path
                from item_definition i
                where i.household_id = ? and i.id = ?
                """, event.householdId(), event.itemId(), event.householdId(), event.itemId());
        if (!items.isEmpty()) {
                    Map<String, Object> item = items.getFirst();
                    String name = item.get("name").toString();
                    String category = item.get("category_path") == null ? null : item.get("category_path").toString();
                    String searchable = String.join(" ", List.of(name,
                            safe(item.get("brand")), safe(item.get("model")),
                            safe(item.get("specification")), safe(category), String.join(" ", tags)))
                            .replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
                    jdbc.update("""
                            insert into catalog_search_projection(item_definition_id, household_id, display_name,
                                category_path, brand, model, specification, tags, raw_barcodes,
                                normalized_barcodes, searchable_text, archived, updated_at)
                            values (?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[], ?, ?, now())
                            on conflict (item_definition_id) do update set
                                display_name=excluded.display_name, category_path=excluded.category_path,
                                brand=excluded.brand, model=excluded.model, specification=excluded.specification,
                                tags=excluded.tags, raw_barcodes=excluded.raw_barcodes,
                                normalized_barcodes=excluded.normalized_barcodes,
                                searchable_text=excluded.searchable_text, archived=excluded.archived, updated_at=now()
                            """, event.itemId(), event.householdId(), name, category,
                            item.get("brand"), item.get("model"), item.get("specification"), array(tags), array(rawBarcodes),
                            array(normalizedBarcodes), searchable, item.get("archived"));
        }
    }
    private String safe(Object value) { return value == null ? "" : value.toString(); }
    private String array(List<String> values) {
        return "{" + values.stream().map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "}";
    }
}
