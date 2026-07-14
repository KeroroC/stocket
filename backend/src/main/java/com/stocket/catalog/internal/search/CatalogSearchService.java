package com.stocket.catalog.internal.search;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.catalog.CatalogExportQuery;
import com.stocket.catalog.CatalogExportRow;
import com.stocket.catalog.CatalogFilter;

@Service
class CatalogSearchService implements CatalogExportQuery {
    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider current;

    CatalogSearchService(JdbcTemplate jdbc, CurrentHouseholdProvider current) {
        this.jdbc = jdbc; this.current = current;
    }

    CatalogSearchResult search(CatalogFilter filter, int page, int size) {
        try { filter.validate(true); } catch (IllegalArgumentException error) { throw new InvalidSearchException(); }
        String query = filter.normalizedQuery();
        boolean includeArchived = filter.includeArchived();
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidSearchException();
        }
        UUID householdId = current.requireCurrent().householdId();
        List<CatalogSearchResult.SearchItem> barcode = jdbc.query("""
                select p.item_definition_id, p.display_name, p.category_path, p.brand, p.model, p.specification,
                       p.tags, p.raw_barcodes
                from catalog_search_projection p
                join item_definition i on i.id = p.item_definition_id
                where p.household_id = ? and (? or not p.archived)
                  and (cast(? as uuid) is null or i.category_id = ?)
                  and ? = any(p.normalized_barcodes)
                order by p.item_definition_id
                """, (resultSet, row) -> map(resultSet, "BARCODE_EXACT"),
                householdId, includeArchived, filter.categoryId(), filter.categoryId(), query.toUpperCase(Locale.ROOT));
        if (!barcode.isEmpty()) {
            return new CatalogSearchResult(barcode, 0, size, barcode.size());
        }

        Long total = jdbc.queryForObject("""
                select count(*) from catalog_search_projection p
                join item_definition i on i.id = p.item_definition_id
                where p.household_id = ? and (? or not p.archived)
                  and (cast(? as uuid) is null or i.category_id = ?)
                  and (p.searchable_text like '%' || ? || '%' or similarity(p.searchable_text, ?) >= 0.15)
                """, Long.class, householdId, includeArchived, filter.categoryId(), filter.categoryId(), query, query);
        List<CatalogSearchResult.SearchItem> items = jdbc.query("""
                select p.item_definition_id, p.display_name, p.category_path, p.brand, p.model, p.specification,
                       p.tags, p.raw_barcodes
                from catalog_search_projection p
                join item_definition i on i.id = p.item_definition_id
                where p.household_id = ? and (? or not p.archived)
                  and (cast(? as uuid) is null or i.category_id = ?)
                  and (p.searchable_text like '%' || ? || '%' or similarity(p.searchable_text, ?) >= 0.15)
                order by case when p.searchable_text like ? || '%' then 0 else 1 end,
                         similarity(p.searchable_text, ?) desc, p.display_name asc, p.item_definition_id asc
                limit ? offset ?
                """, (resultSet, row) -> map(resultSet, "TEXT"),
                householdId, includeArchived, filter.categoryId(), filter.categoryId(), query, query, query, query, size, page * size);
        return new CatalogSearchResult(items, page, size, total == null ? 0 : total);
    }

    @Override
    public long countForExport(UUID householdId, CatalogFilter filter) {
        filter.validate(false);
        String query = filter.normalizedQuery();
        Long total = jdbc.queryForObject("""
                select count(*)
                from catalog_search_projection p
                join item_definition i on i.id = p.item_definition_id
                where p.household_id = ? and (? or not p.archived)
                  and (cast(? as uuid) is null or i.category_id = ?)
                  and (? = '' or ? = any(p.normalized_barcodes)
                       or p.searchable_text like '%' || ? || '%' or similarity(p.searchable_text, ?) >= 0.15)
                """, Long.class, householdId, filter.includeArchived(), filter.categoryId(), filter.categoryId(),
                query, query.toUpperCase(Locale.ROOT), query, query);
        return total == null ? 0 : total;
    }

    @Override
    public List<CatalogExportRow> exportPage(UUID householdId, CatalogFilter filter, UUID afterId, int size) {
        filter.validate(false);
        String query = filter.normalizedQuery();
        return jdbc.query("""
                select p.item_definition_id, p.display_name, p.category_path, p.brand, p.model,
                       p.specification, p.tags, p.raw_barcodes
                from catalog_search_projection p
                join item_definition i on i.id = p.item_definition_id
                where p.household_id = ? and (? or not p.archived)
                  and (cast(? as uuid) is null or i.category_id = ?)
                  and (? = '' or ? = any(p.normalized_barcodes)
                       or p.searchable_text like '%' || ? || '%' or similarity(p.searchable_text, ?) >= 0.15)
                  and (cast(? as uuid) is null or p.item_definition_id > ?)
                order by p.item_definition_id
                limit ?
                """, (resultSet, row) -> new CatalogExportRow(
                        resultSet.getObject("item_definition_id", UUID.class), resultSet.getString("display_name"),
                        resultSet.getString("category_path"), resultSet.getString("brand"), resultSet.getString("model"),
                        resultSet.getString("specification"), array(resultSet.getArray("tags")),
                        array(resultSet.getArray("raw_barcodes"))),
                householdId, filter.includeArchived(), filter.categoryId(), filter.categoryId(), query,
                query.toUpperCase(Locale.ROOT), query, query, afterId, afterId, size);
    }

    private CatalogSearchResult.SearchItem map(java.sql.ResultSet resultSet, String matchType) throws SQLException {
        return new CatalogSearchResult.SearchItem(
                resultSet.getObject("item_definition_id", UUID.class), resultSet.getString("display_name"),
                resultSet.getString("category_path"), resultSet.getString("brand"), resultSet.getString("model"),
                resultSet.getString("specification"), array(resultSet.getArray("tags")),
                array(resultSet.getArray("raw_barcodes")), matchType);
    }

    private List<String> array(Array value) throws SQLException {
        return value == null ? List.of() : Arrays.asList((String[]) value.getArray());
    }
    static class InvalidSearchException extends RuntimeException { }
}
