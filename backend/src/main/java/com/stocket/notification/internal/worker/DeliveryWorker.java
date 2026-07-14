package com.stocket.notification.internal.worker;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.stocket.notification.internal.delivery.BackoffPolicy;

@Component
public class DeliveryWorker {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final List<ChannelSender> senders;
    private final BackoffPolicy backoff;
    private final int maxAttempts;

    DeliveryWorker(JdbcTemplate jdbc, TransactionTemplate transactions, List<ChannelSender> senders,
                   BackoffPolicy backoff,
                   @Value("${stocket.notification.max-attempts:8}") int maxAttempts) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.senders = List.copyOf(senders);
        this.backoff = backoff;
        this.maxAttempts = maxAttempts;
    }

    public int runBatch(int limit) {
        int processed = 0;
        while (processed < limit && runOnce()) {
            processed++;
        }
        return processed;
    }

    public boolean runOnce() {
        Optional<DeliveryAttempt> claimed = claimOne();
        if (claimed.isEmpty()) return false;
        DeliveryAttempt attempt = claimed.get();
        ChannelSender sender = senders.stream()
                .filter(candidate -> candidate.channelType().equals(attempt.channelType()))
                .findFirst().orElse(null);
        SendResult result;
        try {
            result = sender == null
                    ? SendResult.permanent("CHANNEL_SENDER_MISSING")
                    : sender.send(attempt);
        } catch (RuntimeException exception) {
            result = SendResult.retry("NETWORK_ERROR", null);
        }
        complete(attempt, result);
        return true;
    }

    public Optional<DeliveryAttempt> claimOne() {
        return transactions.execute(status -> {
            String owner = UUID.randomUUID().toString();
            List<DeliveryAttempt> claimed = jdbc.query("""
                    with candidate as (
                        select id from notification_delivery
                        where ((status in ('PENDING','RETRY_WAIT')
                                  and (next_attempt_at is null or next_attempt_at<=now()))
                               or (status='PROCESSING' and lease_until<now()))
                        order by coalesce(next_attempt_at,created_at),id
                        for update skip locked
                        limit 1
                    )
                    update notification_delivery delivery
                    set status='PROCESSING',lease_owner=?,lease_until=now()+interval '5 minutes',
                        attempt_count=attempt_count+1,updated_at=now()
                    from candidate
                    where delivery.id=candidate.id
                    returning delivery.id,delivery.household_id,delivery.reminder_id,delivery.member_id,
                              delivery.channel_type,delivery.channel_id,delivery.attempt_count,delivery.lease_owner
                    """, (result, row) -> new DeliveryAttempt(
                    result.getObject("id", UUID.class),
                    result.getObject("household_id", UUID.class),
                    result.getObject("reminder_id", UUID.class),
                    result.getObject("member_id", UUID.class),
                    result.getString("channel_type"),
                    result.getObject("channel_id", UUID.class),
                    result.getInt("attempt_count"),
                    result.getString("lease_owner")), owner);
            return claimed.stream().findFirst();
        });
    }

    private void complete(DeliveryAttempt attempt, SendResult result) {
        transactions.executeWithoutResult(status -> {
            Instant now = Instant.now();
            if ("DELIVERED".equals(result.outcome())) {
                jdbc.update("""
                        update notification_delivery
                        set status='DELIVERED',lease_owner=null,lease_until=null,next_attempt_at=null,
                            delivered_at=?,last_error_code=null,updated_at=?
                        where id=? and lease_owner=? and status='PROCESSING'
                        """, Timestamp.from(now), Timestamp.from(now), attempt.id(), attempt.leaseOwner());
                return;
            }

            boolean dead = "PERMANENT_FAILURE".equals(result.outcome())
                    || attempt.attemptCount() >= maxAttempts;
            Instant next = dead ? null : backoff.nextAttempt(
                    attempt.id(), attempt.attemptCount() - 1, now, result.retryAfter().orElse(null));
            jdbc.update("""
                    update notification_delivery
                    set status=?,lease_owner=null,lease_until=null,next_attempt_at=?,last_error_code=?,
                        last_error_at=?,updated_at=?
                    where id=? and lease_owner=? and status='PROCESSING'
                    """, dead ? "DEAD" : "RETRY_WAIT", next == null ? null : Timestamp.from(next),
                    result.errorCode(), Timestamp.from(now), Timestamp.from(now),
                    attempt.id(), attempt.leaseOwner());
        });
    }
}
