package com.example.reminder.engine;

import com.example.reminder.model.DispatchEvent;
import com.example.reminder.model.ReminderStatus;
import com.example.reminder.queue.NotificationQueue;
import com.example.reminder.storage.ReminderDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumer fleet pulling dispatch events from the queue, enforcing idempotency,
 * delivering notifications, and updating database status asynchronously.
 */
public class NotificationConsumer implements AutoCloseable {

    private final NotificationQueue queue;
    private final IdempotencyStore idempotencyStore;
    private final PushGateway pushGateway;
    private final ReminderDatabase database;
    private final ExecutorService consumerPool;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public NotificationConsumer(
            NotificationQueue queue,
            IdempotencyStore idempotencyStore,
            PushGateway pushGateway,
            ReminderDatabase database,
            int threadCount
    ) {
        this.queue = queue;
        this.idempotencyStore = idempotencyStore;
        this.pushGateway = pushGateway;
        this.database = database;
        this.consumerPool = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "notification-consumer");
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < threadCount; i++) {
            consumerPool.submit(this::consumeLoop);
        }
    }

    private void consumeLoop() {
        List<String> ackBuffer = new ArrayList<>();
        long lastFlush = System.currentTimeMillis();

        while (running.get() || queue.size() > 0) {
            try {
                DispatchEvent event = queue.poll(100);
                if (event != null) {
                    if (idempotencyStore.tryAcquire(event.idempotencyKey(), 86400)) {
                        pushGateway.sendPushNotification(event);
                        ackBuffer.add(event.reminderId());
                    }
                }

                // Batch flush status updates to DB every 50 items or 200ms
                long now = System.currentTimeMillis();
                if (ackBuffer.size() >= 50 || (now - lastFlush > 200 && !ackBuffer.isEmpty())) {
                    database.batchUpdateStatus(ackBuffer, ReminderStatus.DELIVERED);
                    ackBuffer.clear();
                    lastFlush = now;
                }
            } catch (InterruptedException e) {
                if (!running.get() && queue.size() == 0) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Flush remaining buffer on shutdown
        if (!ackBuffer.isEmpty()) {
            database.batchUpdateStatus(ackBuffer, ReminderStatus.DELIVERED);
            ackBuffer.clear();
        }
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            consumerPool.shutdown();
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return consumerPool.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        shutdown();
    }
}
