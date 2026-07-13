package com.stocket.location.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.CurrentHouseholdProvider;

@Service
class LocationService {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private final LocationRepository repository;
    private final CurrentHouseholdProvider current;
    private final LocationCodeGenerator codes;

    LocationService(LocationRepository repository, CurrentHouseholdProvider current, LocationCodeGenerator codes) {
        this.repository = repository; this.current = current; this.codes = codes;
    }

    @Transactional(readOnly = true)
    List<LocationResponse> list(boolean includeArchived) {
        List<Location> all = repository.findByHouseholdId(current.requireCurrent().householdId()).stream()
                .filter(location -> includeArchived || !location.archived()).toList();
        List<LocationResponse> result = new ArrayList<>();
        append(all, null, result);
        return result;
    }

    @Transactional
    LocationResponse create(LocationRequest request) {
        UUID householdId = current.requireCurrent().householdId();
        Location parent = parent(householdId, request.parentId());
        String name = clean(request.name());
        assertAvailable(householdId, parent, normalize(name), null);
        Location location = new Location(UUID.randomUUID(), householdId, parent, name, normalize(name),
                codes.generateCode(), Instant.now());
        return response(repository.saveAndFlush(location));
    }

    @Transactional
    LocationResponse update(UUID id, LocationRequest request) {
        UUID householdId = current.requireCurrent().householdId();
        Location location = require(householdId, id);
        requireVersion(request.version(), location.version());
        Location parent = parent(householdId, request.parentId());
        for (Location ancestor = parent; ancestor != null; ancestor = ancestor.parent()) {
            if (ancestor.id().equals(id)) throw new LocationCycleException();
        }
        String name = clean(request.name());
        assertAvailable(householdId, parent, normalize(name), id);
        location.update(parent, name, normalize(name), Instant.now());
        return response(repository.saveAndFlush(location));
    }

    @Transactional
    LocationResponse archive(UUID id, long version) {
        Location location = requireCurrent(id); requireVersion(version, location.version());
        location.archive(Instant.now()); return response(repository.saveAndFlush(location));
    }

    @Transactional
    LocationResponse restore(UUID id, long version) {
        Location location = requireCurrent(id); requireVersion(version, location.version());
        location.restore(Instant.now()); return response(repository.saveAndFlush(location));
    }

    @Transactional(readOnly = true)
    LocationResponse resolve(String payload) {
        String code;
        try { code = codes.parsePayload(payload); }
        catch (IllegalArgumentException exception) { throw new LocationCodeNotFoundException(); }
        return repository.findByHouseholdIdAndPublicCode(current.requireCurrent().householdId(), code)
                .map(this::response).orElseThrow(LocationCodeNotFoundException::new);
    }

    private void append(List<Location> all, Location parent, List<LocationResponse> result) {
        all.stream().filter(location -> parent == null ? location.parent() == null
                        : location.parent() != null && location.parent().id().equals(parent.id()))
                .sorted(Comparator.comparing(Location::normalizedName).thenComparing(Location::id))
                .forEach(location -> { result.add(response(location)); append(all, location, result); });
    }
    private Location requireCurrent(UUID id) { return require(current.requireCurrent().householdId(), id); }
    private Location require(UUID householdId, UUID id) {
        return repository.findByHouseholdIdAndId(householdId, id).orElseThrow(LocationNotFoundException::new);
    }
    private Location parent(UUID householdId, UUID id) { return id == null ? null : require(householdId, id); }
    private void assertAvailable(UUID householdId, Location parent, String name, UUID excluded) {
        boolean exists = excluded == null
                ? repository.existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNull(householdId, parent, name)
                : repository.existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNullAndIdNot(
                        householdId, parent, name, excluded);
        if (exists) throw new LocationNameConflictException();
    }
    private void requireVersion(Long requested, long actual) {
        if (requested == null || requested != actual) throw new LocationVersionConflictException();
    }
    private String clean(String name) { return WHITESPACE.matcher(name.strip()).replaceAll(" "); }
    private String normalize(String name) { return clean(name).toLowerCase(Locale.ROOT); }
    private LocationResponse response(Location location) {
        return new LocationResponse(location.id(), location.parent() == null ? null : location.parent().id(),
                location.name(), fullPath(location), location.publicCode(), location.version(), location.archived());
    }
    private String fullPath(Location location) {
        List<String> names = new ArrayList<>();
        for (Location currentLocation = location; currentLocation != null; currentLocation = currentLocation.parent()) {
            names.addFirst(currentLocation.name());
        }
        return String.join(" > ", names);
    }

    static class LocationNotFoundException extends RuntimeException { }
    static class LocationCodeNotFoundException extends RuntimeException { }
    static class LocationNameConflictException extends RuntimeException { }
    static class LocationCycleException extends RuntimeException { }
    static class LocationVersionConflictException extends RuntimeException { }
}
