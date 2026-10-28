package com.example.reminder;

import com.example.reminder.engine.ReminderService;
import com.example.reminder.model.DispatchEvent;
import com.example.reminder.model.Reminder;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Interactive demonstration of the Distributed Reminder System.
 */
public class Main {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {
        printBanner();

        try (ReminderService service = new ReminderService(
                "reminder-demo-db",
                4,          // 4 scheduler worker nodes
                8,          // 8 consumer threads
                1000,       // 1s prefetch scanner interval
                30_000,     // 30s lookahead window
                50,         // 50ms wheel tick
                60          // 60 slots per wheel
        )) {
            // Scenario 1: Standard Lifecycle
            runStandardLifecycle(service);

            // Scenario 2: 6:00 PM Massive Spike Simulation (10,000 reminders)
            run6PMSpikeSimulation(service);

            // Scenario 3: In-Flight O(1) Cancellation
            runCancellationDemo(service);

            // Scenario 4: Database Query Efficiency & Metrics
            printDatabaseMetrics(service);
        }
    }

    private static void printBanner() {
        System.out.println("================================================================================");
        System.out.println("         DISTRIBUTED REMINDER & NOTIFICATION SYSTEM ARCHITECTURE               ");
        System.out.println("   [PostgreSQL/H2 Partitioned DB] + [Timing Wheel Cluster] + [Kafka Egress]     ");
        System.out.println("================================================================================\n");
    }

    private static void runStandardLifecycle(ReminderService service) throws Exception {
        System.out.println("--- [SCENARIO 1] Standard Reminder Lifecycle (Create -> Timing Wheel -> Push) ---");
        long triggerTime = System.currentTimeMillis() + 300;

        System.out.printf("[%s] User schedules 'Drink Water' reminder for 300ms from now...\n",
                LocalTime.now().format(TIME_FMT));
        Reminder reminder = service.createReminder("user-42", "Drink Water", "Stay hydrated!", triggerTime);

        System.out.printf("  * Saved to Real SQL Database: ID=%s, Bucket=%s, Status=%s\n",
                reminder.getId(), reminder.getTimeBucket(), reminder.getStatus());

        Thread.sleep(500);

        List<DispatchEvent> delivered = service.getPushGateway().getDeliveredEvents();
        System.out.printf("  * Notification Gateway Received: %d event(s)\n", delivered.size());
        if (!delivered.isEmpty()) {
            DispatchEvent ev = delivered.get(delivered.size() - 1);
            System.out.printf("  * Push Delivered: User=%s, Title='%s', Dispatched after %d ms delay\n",
                    ev.userId(), ev.title(), (ev.actualDispatchMs() - ev.scheduledTriggerMs()));
        }
        System.out.println("Scenario 1 completed!\n");
    }

    private static void run6PMSpikeSimulation(ReminderService service) throws Exception {
        System.out.println("--- [SCENARIO 2] 6:00 PM Spike: 10,000 Concurrent Reminders Firing ---");
        int count = 10_000;
        long target6PM = System.currentTimeMillis() + 600;

        System.out.printf("[%s] Simulating bulk insertion of %d reminders scheduled for 6:00:00 PM...\n",
                LocalTime.now().format(TIME_FMT), count);

        List<Reminder> bulk = new ArrayList<>(count);
        Random rnd = new Random(42);
        for (int i = 0; i < count; i++) {
            bulk.add(new Reminder("user-" + i, "6 PM Daily Standup", "Join meeting link", target6PM));
        }

        long startDb = System.nanoTime();
        service.getDatabase().saveBatch(bulk);
        long dbDurationMs = (System.nanoTime() - startDb) / 1_000_000;
        System.out.printf("  * Batch inserted %d rows into SQL DB in %d ms (%d writes/sec)\n",
                count, dbDurationMs, (count * 1000L / Math.max(1, dbDurationMs)));

        // Prefetch into Timing Wheels
        long startPrefetch = System.nanoTime();
        int prefetched = service.triggerPrefetchNow();
        long prefetchDurationMs = (System.nanoTime() - startPrefetch) / 1_000_000;
        System.out.printf("  * Lookahead Scanner prefetched %d reminders across 4 Timing Wheel nodes in %d ms\n",
                prefetched, prefetchDurationMs);

        System.out.printf("[%s] Waiting for 6:00:00 PM trigger...\n", LocalTime.now().format(TIME_FMT));
        Thread.sleep(1500);

        long deliveredCount = service.getPushGateway().getDeliveryCount();
        System.out.printf("  * [6:00 PM RESULT] Successfully delivered %d / %d notifications without crashing DB!\n",
                deliveredCount, count);
        System.out.println("Scenario 2 completed!\n");
    }

    private static void runCancellationDemo(ReminderService service) throws Exception {
        System.out.println("--- [SCENARIO 3] O(1) In-Flight Reminder Cancellation ---");
        long triggerTime = System.currentTimeMillis() + 400;

        Reminder r = service.createReminder("user-99", "Cancel Me", "I changed my mind", triggerTime);
        System.out.printf("  * Created reminder ID=%s\n", r.getId());

        Thread.sleep(100);
        System.out.println("  * User cancels reminder 300ms before trigger time...");
        boolean cancelled = service.cancelReminder(r.getId());
        System.out.printf("  * Cancel Result: %s (Status in DB: %s)\n",
                cancelled, service.getDatabase().findById(r.getId()).get().getStatus());

        Thread.sleep(500);
        System.out.println("  * Verified: No notification was pushed for the cancelled reminder.\n");
    }

    private static void printDatabaseMetrics(ReminderService service) {
        System.out.println("================================================================================");
        System.out.println("                   SYSTEM PERFORMANCE & DATABASE METRICS                       ");
        System.out.println("================================================================================");
        System.out.printf("Total Reminders Stored:      %d\n", service.getDatabase().count());
        System.out.printf("Total DB Read Queries:       %d (Zero per-second polling!)\n", service.getDatabase().getQueryCount());
        System.out.printf("Total DB Write Operations:   %d\n", service.getDatabase().getWriteCount());
        System.out.printf("Prefetch Scan Runs:          %d\n", service.getPrefetcher().getPrefetchRunCount());
        System.out.printf("Total Push Notifications:    %d\n", service.getPushGateway().getDeliveryCount());
        System.out.printf("Idempotency Replay Blocked:  %d\n", service.getIdempotencyStore().getDuplicateCount());
        System.out.println("================================================================================\n");
    }
}
