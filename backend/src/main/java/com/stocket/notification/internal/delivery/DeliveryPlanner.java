package com.stocket.notification.internal.delivery;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.notification.NotificationRequested;

@Service
public class DeliveryPlanner {

    private final JdbcTemplate jdbc;

    DeliveryPlanner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @ApplicationModuleListener(id = "notification.reminder-requested")
    void on(NotificationRequested event) {
        plan(event);
    }

    @Transactional
    public void plan(NotificationRequested event) {
        Integer reminderCount = jdbc.queryForObject("""
                select count(*) from reminder where id=? and household_id=? and status in ('OPEN','ACKNOWLEDGED')
                """, Integer.class, event.reminderId(), event.householdId());
        if (reminderCount == null || reminderCount == 0) return;

        List<Member> members = jdbc.query("""
                select member.id,account.email
                from household_member member
                join user_account account on account.id=member.account_id
                where member.household_id=? and account.status='ACTIVE'
                order by member.id
                """, (result, row) -> new Member(
                result.getObject("id", UUID.class), result.getString("email")), event.householdId());
        List<Channel> channels = jdbc.query("""
                select id,type from notification_channel
                where household_id=? and enabled=true order by type,id
                """, (result, row) -> new Channel(
                result.getObject("id", UUID.class), result.getString("type")), event.householdId());

        for (Member member : members) {
            for (Channel channel : channels) {
                if (eligible(event.householdId(), member, channel)) {
                    insert(event, member.id(), channel);
                }
            }
        }
    }

    private boolean eligible(UUID householdId, Member member, Channel channel) {
        return switch (channel.type()) {
            case "SMTP" -> member.email() != null && !member.email().isBlank();
            case "WEB_PUSH" -> Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from push_subscription
                        where household_id=? and member_id=? and enabled=true)
                    """, Boolean.class, householdId, member.id()));
            default -> true;
        };
    }

    private void insert(NotificationRequested event, UUID memberId, Channel channel) {
        String dedupeKey = event.reminderId() + ":" + memberId + ":" + channel.type() + ":" + channel.id();
        jdbc.update("""
                insert into notification_delivery(id,household_id,reminder_id,member_id,channel_type,
                    channel_id,dedupe_key,request_id,status,attempt_count,next_attempt_at,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,'PENDING',0,now(),now(),now())
                on conflict (dedupe_key) do nothing
                """, UUID.randomUUID(), event.householdId(), event.reminderId(), memberId,
                channel.type(), channel.id(), dedupeKey, event.requestId());
    }

    private record Member(UUID id, String email) {
    }

    private record Channel(UUID id, String type) {
    }
}
