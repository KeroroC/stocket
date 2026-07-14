package com.stocket.notification.internal.channel;

import org.springframework.stereotype.Component;

import com.stocket.notification.internal.worker.ChannelSender;
import com.stocket.notification.internal.worker.DeliveryAttempt;
import com.stocket.notification.internal.worker.SendResult;

@Component
public class InAppSender implements ChannelSender {

    @Override
    public String channelType() {
        return "IN_APP";
    }

    @Override
    public SendResult send(DeliveryAttempt attempt) {
        return SendResult.delivered();
    }
}
