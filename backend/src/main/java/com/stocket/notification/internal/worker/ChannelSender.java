package com.stocket.notification.internal.worker;

public interface ChannelSender {

    String channelType();

    SendResult send(DeliveryAttempt attempt);
}
