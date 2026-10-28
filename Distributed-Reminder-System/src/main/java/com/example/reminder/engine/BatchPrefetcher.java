package com.example.reminder.engine;

import com.example.reminder.model.Reminder;
import com.example.reminder.model.ReminderStatus;
import com.example.reminder.storage.ReminderDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background worker that pre-fetches upcoming reminders in large time windows (e.g. next 10 minutes)
 * from the database, preventing per-second database polling.
 */
public class BatchPrefetcher implements AutoCloseable {

    private final ReminderDatabase database;
    private final ConsistentHashRouter router;
    private final long lookaheadWindowMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicLong prefetchRunCount = new AtomicLong(0);
    private final AtomicLong prefetchedRemindersCount = new AtomicLong(0);

    public BatchPrefetcher(
            ReminderDatabase database,
            ConsistentHashRouter router,
            long scanIntervalMs,
            long lookaheadWindowMs
    ) {
        this.database = database;
        this.router = router;
        this.lookaheadWindowMs = lookaheadWindowMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "batch-prefetcher");
            t.setDaemon(true);
            return t;
        });

        this.scheduler.scheduleAtFixedRate(this::prefetchWindow, 0, scanIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Executes a single batch pre-fetch query over the database.
     */
    public synchronized int prefetchWindow() {
        if (!running.get()) return 0;

        prefetchRunCount.incrementAndGet();
        long now = System.currentTimeMillis();
        long windowEnd = now + lookaheadWindowMs;

        List<Reminder> upcoming = database.findRemindersForWindow(
                now, windowEnd, ReminderStatus.PENDING
        );

        if (upcoming.isEmpty()) {
            return 0;
        }

        List<String> prefetchedIds = new ArrayList<>(upcoming.size());
        for (Reminder reminder : upcoming) {
            SchedulerNode node = router.getNode(reminder.getId());
            if (node != null) {
                node.schedule(reminder);
                prefetchedIds.add(reminder.getId());
            }
        }

        database.batchUpdateStatus(prefetchedIds, ReminderStatus.PREFETCHED);
        prefetchedRemindersCount.addAndGet(prefetchedIds.size());
        return prefetchedIds.size();
    }

    public long getPrefetchRunCount() {
        return prefetchRunCount.get();
    }

    public long getPrefetchedRemindersCount() {
        return prefetchedRemindersCount.get();
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}
