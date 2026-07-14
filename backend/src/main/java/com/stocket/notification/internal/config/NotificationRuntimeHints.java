package com.stocket.notification.internal.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import com.stocket.notification.NotificationRequested;
import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.worker.DeliveryAttempt;
import com.stocket.notification.internal.worker.SendResult;

@Configuration
@ImportRuntimeHints(NotificationRuntimeHints.Hints.class)
public class NotificationRuntimeHints {

    public static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (Class<?> type : new Class<?>[] {
                    NotificationRequested.class, EncryptedSecret.class,
                    DeliveryAttempt.class, SendResult.class
            }) {
                hints.reflection().registerType(type,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);
            }
        }
    }
}
