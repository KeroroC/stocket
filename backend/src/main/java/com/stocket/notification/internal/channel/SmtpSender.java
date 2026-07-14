package com.stocket.notification.internal.channel;

import java.util.Map;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.crypto.SecretCipher;
import com.stocket.notification.internal.worker.ChannelSender;
import com.stocket.notification.internal.worker.DeliveryAttempt;
import com.stocket.notification.internal.worker.SendResult;

@Component
public class SmtpSender implements ChannelSender {

    private final NotificationChannelRepository channels;
    private final SecretCipher cipher;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    SmtpSender(NotificationChannelRepository channels, SecretCipher cipher,
               org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.channels = channels;
        this.cipher = cipher;
        this.jdbc = jdbc;
    }

    @Override
    public String channelType() {
        return "SMTP";
    }

    @Override
    public SendResult send(DeliveryAttempt attempt) {
        NotificationChannel channel = channels.findByHouseholdIdAndId(
                attempt.householdId(), attempt.channelId()).orElse(null);
        if (channel == null || !channel.enabled()) return SendResult.permanent("CHANNEL_DISABLED");
        String recipient = jdbc.query("""
                select account.email from household_member member
                join user_account account on account.id=member.account_id
                where member.household_id=? and member.id=?
                """, (result, row) -> result.getString(1), attempt.householdId(), attempt.memberId())
                .stream().findFirst().orElse(null);
        if (recipient == null || recipient.isBlank()) return SendResult.permanent("RECIPIENT_MISSING");
        Map<String, Object> config = channel.configuration();
        try {
            JavaMailSenderImpl mail = new JavaMailSenderImpl();
            mail.setHost(String.valueOf(config.get("host")));
            mail.setPort(((Number) config.get("port")).intValue());
            mail.setUsername(String.valueOf(config.getOrDefault("username", "")));
            if (channel.hasSecret()) {
                mail.setPassword(cipher.decrypt(
                        new EncryptedSecret(channel.encryptedSecret(), channel.keyVersion()), aad(channel)));
            }
            java.util.Properties properties = mail.getJavaMailProperties();
            properties.put("mail.smtp.connectiontimeout", "10000");
            properties.put("mail.smtp.timeout", "15000");
            properties.put("mail.smtp.writetimeout", "15000");
            String tlsMode = String.valueOf(config.getOrDefault("tlsMode", "STARTTLS"));
            properties.put("mail.smtp.starttls.enable", Boolean.toString("STARTTLS".equals(tlsMode)));
            properties.put("mail.smtp.ssl.enable", Boolean.toString("TLS".equals(tlsMode)));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.valueOf(config.get("fromAddress")));
            message.setTo(recipient);
            message.setSubject("Stocket reminder");
            message.setText("A Stocket reminder is ready. Delivery ID: " + attempt.deliveryKey());
            mail.send(message);
            return SendResult.delivered();
        } catch (MailAuthenticationException exception) {
            return SendResult.permanent("SMTP_AUTHENTICATION");
        } catch (RuntimeException exception) {
            return SendResult.retry("SMTP_IO", null);
        }
    }

    private String aad(NotificationChannel channel) {
        return channel.householdId() + ":" + channel.id() + ":" + channel.type();
    }
}
