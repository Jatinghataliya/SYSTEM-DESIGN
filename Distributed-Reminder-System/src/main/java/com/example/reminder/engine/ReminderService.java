package com.example.reminder.engine;

import com.example.reminder.model.Reminder;
import com.example.reminder.model.ReminderStatus;
import com.example.reminder.queue.NotificationQueue;
import com.example.reminder.storage.ReminderDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * High-level unified facade for the Distributed Reminder System.
 */
public class ReminderService implements AutoCloseable {

    private final ReminderDatabase database;
    private final NotificationQueue notificationQueue;
    private final IdempotencyStore idempotencyStore;
    private final PushGateway pushGateway;
    private final List<SchedulerNode> schedulerNodes = new ArrayList<>();
    private final ConsistentHashRouter router;
    private final BatchPrefetcher prefetcher;
    private final NotificationConsumer consumer;

    private final long lookaheadWindowMs;

    public ReminderService(
            String dbName,
            int workerNodeCount,
            int consumerThreadCount,
            long scanIntervalMs,
            long lookaheadWindowMs,
            long wheelTickMs,
            int wheelSize
    ) {
        this.database = ReminderDatabase.createInMemory(dbName);
        this.notificationQueue = new NotificationQueue();
        this.idempotencyStore = new IdempotencyStore();
        this.pushGateway = new PushGateway();
        this.lookaheadWindowMs = lookaheadWindowMs;

        for (int i = 0; i < workerNodeCount; i++) {
            SchedulerNode node = new SchedulerNode(
                    "scheduler-node-" + i,
                    notificationQueue,
                    wheelTickMs,
                    wheelSize
            );
            schedulerNodes.add(node);
        }

        this.router = new ConsistentHashRouter(100, schedulerNodes);
        this.prefetcher = new BatchPrefetcher(database, router, scanIntervalMs, lookaheadWindowMs);
        this.consumer = new NotificationConsumer(
                notificationQueue,
                idempotencyStore,
                pushGateway,
                database,
                consumerThreadCount
        );
    }

    /**
     * Creates and schedules a new reminder.
     */
    public Reminder createReminder(String userId, String title, String message, long triggerTimeMs) {
        Reminder reminder = new Reminder(userId, title, message, triggerTimeMs);
        database.save(reminder);

        long now = System.currentTimeMillis();
        // If the reminder falls within the current active lookahead window, schedule in-memory immediately
        if (triggerTimeMs >= now && triggerTimeMs <= now + lookaheadWindowMs) {
            SchedulerNode node = router.getNode(reminder.getId());
            if (node != null) {
                node.schedule(reminder);
                database.updateStatus(reminder.getId(), ReminderStatus.PREFETCHED);
                reminder.setStatus(ReminderStatus.PREFETCHED);
            }
        }
        return reminder;
    }

    /**
     * Cancels an existing reminder in O(1) time across database and in-memory timing wheel.
     */
    public boolean cancelReminder(String reminderId) {
        Optional<Reminder> opt = database.findById(reminderId);
        if (opt.isPresent()) {
            Reminder reminder = opt.get();
            if (reminder.getStatus() == ReminderStatus.DELIVERED || reminder.getStatus() == ReminderStatus.CANCELLED) {
                return false;
            }

            database.updateStatus(reminderId, ReminderStatus.CANCELLED);
            SchedulerNode node = router.getNode(reminderId);
            if (node != null) {
                node.cancel(reminderId);
            }
            return true;
        }
        return false;
    }

    public int triggerPrefetchNow() {
        return prefetcher.prefetchWindow();
    }

    public ReminderDatabase getDatabase() {
        return database;
    }

    public NotificationQueue getNotificationQueue() {
        return notificationQueue;
    }

    public PushGateway getPushGateway() {
        return pushGateway;
    }

    public IdempotencyStore getIdempotencyStore() {
        return idempotencyStore;
    }

    public ConsistentHashRouter getRouter() {
        return router;
    }

    public List<SchedulerNode> getSchedulerNodes() {
        return schedulerNodes;
    }

    public BatchPrefetcher getPrefetcher() {
        return prefetcher;
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        prefetcher.shutdown();
        for (SchedulerNode node : schedulerNodes) {
            node.shutdown();
        }
        consumer.shutdown();
        database.close();
    }
}
