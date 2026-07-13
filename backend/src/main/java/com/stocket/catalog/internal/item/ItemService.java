package com.stocket.catalog.internal.item;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.catalog.CatalogItemChanged;
import com.stocket.catalog.internal.category.CategoryItemPolicy;
import com.stocket.identity.CurrentHouseholdProvider;

@Service
class ItemService {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final ItemRepository items;
    private final ItemBarcodeRepository barcodes;
    private final CurrentHouseholdProvider currentHousehold;
    private final CategoryItemPolicy categoryPolicy;
    private final ApplicationEventPublisher events;

    ItemService(ItemRepository items, ItemBarcodeRepository barcodes,
                CurrentHouseholdProvider currentHousehold, CategoryItemPolicy categoryPolicy,
                ApplicationEventPublisher events) {
        this.items = items;
        this.barcodes = barcodes;
        this.currentHousehold = currentHousehold;
        this.categoryPolicy = categoryPolicy;
        this.events = events;
    }

    @Transactional(readOnly = true)
    ItemResponse get(UUID id) {
        return ItemMapper.toResponse(requireItem(currentHousehold.requireCurrent().householdId(), id));
    }

    @Transactional
    ItemResponse create(ItemRequest request) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        validateShelfLife(request);
        PreparedValues prepared = prepareCollections(request);
        var attributes = categoryPolicy.validateAttributes(householdId, request.categoryId(), request.customAttributes());
        UUID itemId = UUID.randomUUID();
        ItemDefinition item = new ItemDefinition(itemId, householdId, request.categoryId(), clean(request.name()),
                normalizeText(request.name()), request, attributes, Instant.now());
        return persist(item, prepared, false);
    }

    @Transactional
    ItemResponse update(UUID id, ItemRequest request) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        ItemDefinition item = requireItem(householdId, id);
        requireVersion(request.version(), item.version());
        validateShelfLife(request);
        PreparedValues prepared = prepareCollections(request);
        var attributes = categoryPolicy.validateAttributes(householdId, request.categoryId(), request.customAttributes());
        item.update(clean(request.name()), normalizeText(request.name()), request, attributes, Instant.now());
        return persist(item, prepared, true);
    }

    @Transactional
    ItemResponse archive(UUID id, long version) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        ItemDefinition item = requireItem(householdId, id);
        requireVersion(version, item.version());
        item.archive(Instant.now());
        ItemResponse response = ItemMapper.toResponse(items.saveAndFlush(item));
        events.publishEvent(new CatalogItemChanged(householdId, item.id()));
        return response;
    }

    @Transactional
    ItemResponse restore(UUID id, long version) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        ItemDefinition item = requireItem(householdId, id);
        requireVersion(version, item.version());
        categoryPolicy.requireActive(householdId, item.categoryId());
        item.restore(Instant.now());
        ItemResponse response = ItemMapper.toResponse(items.saveAndFlush(item));
        events.publishEvent(new CatalogItemChanged(householdId, item.id()));
        return response;
    }

    private ItemResponse persist(ItemDefinition item, PreparedValues prepared, boolean replacing) {
        try {
            if (replacing) {
                item.replaceBarcodes(List.of());
                item.replaceTags(List.of());
                items.flush();
            }
            for (PreparedBarcode barcode : prepared.barcodes()) {
                if (barcodes.existsByHouseholdIdAndNormalizedValueAndItemDefinitionIdNot(
                        item.householdId(), barcode.normalized(), item.id())) {
                    throw new BarcodeConflictException();
                }
            }
            List<ItemBarcode> replacements = prepared.barcodes().stream()
                    .map(value -> new ItemBarcode(item.householdId(), item, value.raw(), value.normalized(), Instant.now()))
                    .toList();
            List<ItemTag> tagReplacements = prepared.tags().stream()
                    .map(value -> new ItemTag(item.householdId(), item, value.raw(), value.normalized(), Instant.now()))
                    .toList();
            item.replaceBarcodes(replacements);
            item.replaceTags(tagReplacements);
            ItemDefinition saved = items.saveAndFlush(item);
            ItemResponse response = ItemMapper.toResponse(saved);
            events.publishEvent(new CatalogItemChanged(item.householdId(), item.id()));
            return response;
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uq_item_barcode_household_normalized_value")) {
                throw new BarcodeConflictException();
            }
            throw exception;
        }
    }

    private PreparedValues prepareCollections(ItemRequest request) {
        List<PreparedBarcode> preparedBarcodes = request.barcodes().stream()
                .map(this::prepareBarcode).toList();
        assertUnique(preparedBarcodes.stream().map(PreparedBarcode::normalized).toList(), BarcodeConflictException::new);
        List<PreparedTag> preparedTags = request.tags().stream().map(this::prepareTag).toList();
        assertUnique(preparedTags.stream().map(PreparedTag::normalized).toList(), TagConflictException::new);
        return new PreparedValues(preparedBarcodes, preparedTags);
    }

    private PreparedBarcode prepareBarcode(String input) {
        String raw = requireValue(input);
        return new PreparedBarcode(raw, raw.toUpperCase(Locale.ROOT));
    }

    private PreparedTag prepareTag(String input) {
        String raw = requireValue(input);
        return new PreparedTag(raw, raw.toLowerCase(Locale.ROOT));
    }

    private String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCollectionValueException();
        }
        return clean(value);
    }

    private boolean hasConstraint(Throwable exception, String constraint) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private <T extends RuntimeException> void assertUnique(List<String> values,
                                                            java.util.function.Supplier<T> exception) {
        Set<String> unique = new HashSet<>();
        if (values.stream().anyMatch(value -> !unique.add(value))) {
            throw exception.get();
        }
    }

    private void validateShelfLife(ItemRequest request) {
        if ((request.defaultShelfLifeValue() == null) != (request.defaultShelfLifeUnit() == null)) {
            throw new InvalidShelfLifeException();
        }
    }

    private ItemDefinition requireItem(UUID householdId, UUID id) {
        return items.findByHouseholdIdAndId(householdId, id).orElseThrow(ItemNotFoundException::new);
    }

    private void requireVersion(Long requested, long actual) {
        if (requested == null || requested != actual) {
            throw new VersionConflictException();
        }
    }

    private String clean(String value) { return WHITESPACE.matcher(value.strip()).replaceAll(" "); }
    private String normalizeText(String value) { return clean(value).toLowerCase(Locale.ROOT); }

    private record PreparedValues(List<PreparedBarcode> barcodes, List<PreparedTag> tags) { }
    private record PreparedBarcode(String raw, String normalized) { }
    private record PreparedTag(String raw, String normalized) { }

    static class ItemNotFoundException extends RuntimeException { }
    static class BarcodeConflictException extends RuntimeException { }
    static class TagConflictException extends RuntimeException { }
    static class VersionConflictException extends RuntimeException { }
    static class InvalidCollectionValueException extends RuntimeException { }
    static class InvalidShelfLifeException extends RuntimeException { }
}
