package com.stocket.notification;

import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import com.stocket.notification.internal.config.NotificationRuntimeHints;
import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.worker.DeliveryAttempt;
import com.stocket.notification.internal.worker.SendResult;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRuntimeHintsTest {

    @Test
    void registersEventCryptoAndDeliveryPayloadsForNativeSerialization() {
        RuntimeHints hints = new RuntimeHints();
        new NotificationRuntimeHints.Hints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(NotificationRequested.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(EncryptedSecret.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(DeliveryAttempt.class).test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection().onType(SendResult.class).test(hints)).isTrue();
    }
}
