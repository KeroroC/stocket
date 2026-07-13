package com.stocket.catalog.internal.search;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.stocket.identity.CurrentHouseholdProvider;

@Service
class CatalogSearchService {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider current;

    CatalogSearchService(JdbcTemplate jdbc, CurrentHouseholdProvider current) {
        this.jdbc = jdbc; this.current = current;
    }

    CatalogSearchResult search(String input, int page, int size, boolean includeArchived) {
        String query = normalize(input);
        if (query.isBlank() || query.length() > 120 || page < 0 || size < 1 || size > 100) {
            throw new InvalidSearchException();
        }
        UUID householdId = current.requireCurrent().householdId();
        List<CatalogSearchResult.SearchItem> barcode = jdbc.query("""
                select item_definition_id, display_name, category_path, brand, model, specification,
                       tags, raw_barcodes
                from catalog_search_projection
                where household_id = ? and (? or not archived) and ? = any(normalized_barcodes)
                order by item_definition_id
                """, (resultSet, row) -> map(resultSet, "BARCODE_EXACT"),
                householdId, includeArchived, query.toUpperCase(Locale.ROOT));
        if (!barcode.isEmpty()) {
            return new CatalogSearchResult(barcode, 0, size, barcode.size());
        }

        Long total = jdbc.queryForObject("""
                select count(*) from catalog_search_projection
                where household_id = ? and (? or not archived)
                  and (searchable_text like '%' || ? || '%' or similarity(searchable_text, ?) >= 0.15)
                """, Long.class, householdId, includeArchived, query, query);
        List<CatalogSearchResult.SearchItem> items = jdbc.query("""
                select item_definition_id, display_name, category_path, brand, model, specification,
                       tags, raw_barcodes
                from catalog_search_projection
                where household_id = ? and (? or not archived)
                  and (searchable_text like '%' || ? || '%' or similarity(searchable_text, ?) >= 0.15)
                order by case when searchable_text like ? || '%' then 0 else 1 end,
                         similarity(searchable_text, ?) desc, display_name asc, item_definition_id asc
                limit ? offset ?
                """, (resultSet, row) -> map(resultSet, "TEXT"),
                householdId, includeArchived, query, query, query, query, size, page * size);
        return new CatalogSearchResult(items, page, size, total == null ? 0 : total);
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
    private String normalize(String value) {
        return value == null ? "" : WHITESPACE.matcher(value.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
    static class InvalidSearchException extends RuntimeException { }
}
