package com.stocket.system.operations;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OperationsConfiguration {
    @Bean
    MeterBinder operationalGauges(JdbcTemplate jdbc) {
        return registry -> {
            gauge(registry, jdbc, "stocket.events.incomplete", "select count(*) from event_publication where completion_date is null");
            gauge(registry, jdbc, "stocket.deliveries.dead", "select count(*) from notification_delivery where status='DEAD'");
            gauge(registry, jdbc, "stocket.reconciliation.open", "select count(*) from inventory_reconciliation_issue where status='OPEN'");
            gauge(registry, jdbc, "stocket.attachments.missing", "select count(*) from attachment where status='MISSING'");
        };
    }

    private void gauge(io.micrometer.core.instrument.MeterRegistry registry, JdbcTemplate jdbc, String name, String sql) {
        Gauge.builder(name, jdbc, source -> count(source, sql)).register(registry);
    }

    private double count(JdbcTemplate jdbc, String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0 : value.doubleValue();
        } catch (RuntimeException error) {
            return Double.NaN;
        }
    }
}
