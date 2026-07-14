package com.stocket.system.internal.tracing;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.stocket.audit.AuditEvent;
import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.identity.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
@ConditionalOnBean(CurrentHouseholdProvider.class)
class WriteAuditInterceptor implements HandlerInterceptor {
    private final CurrentHouseholdProvider current;
    private final ApplicationEventPublisher events;

    WriteAuditInterceptor(CurrentHouseholdProvider current, ApplicationEventPublisher events) {
        this.current = current; this.events = events;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception error) {
        if (error != null || response.getStatus() >= 400 || !isWrite(request.getMethod())) return;
        String route = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        EventDescriptor descriptor = descriptor(route);
        if (descriptor == null) return;
        CurrentHousehold context = current.requireCurrent();
        events.publishEvent(new AuditEvent(UUID.randomUUID(), context.householdId(), Instant.now(), descriptor.eventType(),
                "SUCCESS", context.accountId(), descriptor.subjectType(), subjectId(request.getRequestURI()),
                RequestContext.requireRequestId(), "api", Map.of("method", request.getMethod(), "route", route)));
    }

    private EventDescriptor descriptor(String route) {
        if (route.startsWith("/api/v1/categories") || route.startsWith("/api/v1/items")) return new EventDescriptor("CatalogChanged", "CATALOG");
        if (route.startsWith("/api/v1/locations")) return new EventDescriptor("LocationChanged", "LOCATION");
        if (route.startsWith("/api/v1/reminders") || route.startsWith("/api/v1/reminder-rules")) return new EventDescriptor("ReminderChanged", "REMINDER");
        if (route.startsWith("/api/v1/notification-channels")) return new EventDescriptor("NotificationChannelChanged", "NOTIFICATION_CHANNEL");
        return null;
    }

    private boolean isWrite(String method) { return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method); }
    private UUID subjectId(String path) {
        for (String segment : path.split("/")) try { return UUID.fromString(segment); } catch (IllegalArgumentException ignored) { }
        return null;
    }
    private record EventDescriptor(String eventType, String subjectType) { }
}
