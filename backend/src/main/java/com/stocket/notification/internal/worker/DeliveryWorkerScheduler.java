package com.stocket.notification.internal.worker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnBooleanProperty(prefix = "stocket.notification", name = "worker-enabled")
class DeliveryWorkerScheduler {

    private final DeliveryWorker worker;
    private final int batchSize;

    DeliveryWorkerScheduler(DeliveryWorker worker,
                            @Value("${stocket.notification.worker-batch-size:50}") int batchSize) {
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${stocket.notification.worker-delay:5s}")
    void run() {
        worker.runBatch(batchSize);
    }
}
