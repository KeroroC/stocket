package com.stocket.reminder.internal.rule;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.stocket.identity.CurrentHouseholdProvider;

@Service
public class ReminderRuleService {

    private static final EffectiveReminderRule DEFAULT_RULE =
            new EffectiveReminderRule(List.of(30, 7, 1, 0), null);

    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider currentHousehold;

    ReminderRuleService(JdbcTemplate jdbc, CurrentHouseholdProvider currentHousehold) {
        this.jdbc = jdbc;
        this.currentHousehold = currentHousehold;
    }

    public static EffectiveReminderRule select(List<ReminderRule> rules, UUID categoryId, UUID itemId) {
        if (rules == null || rules.isEmpty()) {
            return DEFAULT_RULE;
        }

        return find(rules, "ITEM", itemId)
                .or(() -> find(rules, "CATEGORY", categoryId))
                .or(() -> find(rules, "HOUSEHOLD", null))
                .map(ReminderRule::effectiveRule)
                .orElse(DEFAULT_RULE);
    }

    private static java.util.Optional<ReminderRule> find(List<ReminderRule> rules, String scopeType,
                                                          UUID scopeId) {
        return rules.stream()
                .filter(rule -> scopeType.equals(rule.scopeType()))
                .filter(rule -> java.util.Objects.equals(scopeId, rule.scopeId()))
                .findFirst();
    }

    public List<RuleView> list() {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        return jdbc.query("""
                select id,scope_type,scope_id,expiration_offsets,low_stock_threshold,enabled,version
                from reminder_rule where household_id=?
                order by case scope_type when 'HOUSEHOLD' then 0 when 'CATEGORY' then 1 else 2 end, scope_id
                """, (result, row) -> view(result), householdId);
    }

    public RuleView upsert(String requestedScopeType, UUID requestedScopeId, List<Integer> offsets,
                           java.math.BigDecimal threshold, boolean enabled, long version) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        String scopeType = requestedScopeType.toUpperCase(java.util.Locale.ROOT);
        UUID scopeId = validateScope(householdId, scopeType, requestedScopeId);
        EffectiveReminderRule effective = new EffectiveReminderRule(offsets, threshold);
        String array = "{" + effective.expirationOffsets().stream()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "}";
        java.math.BigDecimal storedThreshold = effective.lowStockThreshold().orElse(null);

        List<UUID> existing = jdbc.queryForList("""
                select id from reminder_rule
                where household_id=? and scope_type=? and scope_id is not distinct from ?
                """, UUID.class, householdId, scopeType, scopeId);
        UUID id;
        if (existing.isEmpty()) {
            if (version != 0) throw new VersionConflictException();
            id = UUID.randomUUID();
            jdbc.update("""
                    insert into reminder_rule(id,household_id,scope_type,scope_id,expiration_offsets,
                        low_stock_threshold,enabled,version,created_at,updated_at)
                    values (?,?,?,?,?::integer[],?,?,0,now(),now())
                    """, id, householdId, scopeType, scopeId, array, storedThreshold, enabled);
        } else {
            id = existing.getFirst();
            int updated = jdbc.update("""
                    update reminder_rule
                    set expiration_offsets=?::integer[],low_stock_threshold=?,enabled=?,
                        version=version+1,updated_at=now()
                    where id=? and household_id=? and version=?
                    """, array, storedThreshold, enabled, id, householdId, version);
            if (updated == 0) throw new VersionConflictException();
        }
        return findView(householdId, id);
    }

    private UUID validateScope(UUID householdId, String scopeType, UUID requestedScopeId) {
        if ("HOUSEHOLD".equals(scopeType)) {
            if (!householdId.equals(requestedScopeId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return null;
        }
        String table = switch (scopeType) {
            case "CATEGORY" -> "category";
            case "ITEM" -> "item_definition";
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        };
        Integer count = jdbc.queryForObject(
                "select count(*) from " + table + " where household_id=? and id=?",
                Integer.class, householdId, requestedScopeId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return requestedScopeId;
    }

    private RuleView findView(UUID householdId, UUID id) {
        return jdbc.query("""
                select id,scope_type,scope_id,expiration_offsets,low_stock_threshold,enabled,version
                from reminder_rule where household_id=? and id=?
                """, (result, row) -> view(result), householdId, id).getFirst();
    }

    private RuleView view(java.sql.ResultSet result) throws java.sql.SQLException {
        Integer[] stored = (Integer[]) result.getArray("expiration_offsets").getArray();
        return new RuleView(
                result.getObject("id", UUID.class), result.getString("scope_type"),
                result.getObject("scope_id", UUID.class), List.of(stored),
                result.getBigDecimal("low_stock_threshold"), result.getBoolean("enabled"),
                result.getLong("version"));
    }

    public record RuleView(UUID id, String scopeType, UUID scopeId, List<Integer> expirationOffsets,
                           java.math.BigDecimal lowStockThreshold, boolean enabled, long version) {
    }

    public static final class VersionConflictException extends RuntimeException {
    }
}
