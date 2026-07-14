package com.stocket.catalog;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@org.springframework.modulith.NamedInterface("api")
public record CatalogFilter(String q, UUID categoryId, boolean includeArchived) {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String normalizedQuery() {
        return q == null ? "" : WHITESPACE.matcher(q.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    public void validate(boolean queryRequired) {
        String normalized = normalizedQuery();
        if ((queryRequired && normalized.isBlank()) || normalized.length() > 120) {
            throw new IllegalArgumentException("CATALOG_FILTER_INVALID");
        }
    }
}
