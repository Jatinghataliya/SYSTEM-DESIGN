package com.example.timingwheel;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Interactive demonstration of Hierarchical Timing Wheels in System Design.
 */
public class Main {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {
        printBanner();

        // Scenario 1: Multi-Tier Scheduling & Cascading
        runCascadeDemo();

        // Scenario 2: O(1) Cancellation Demo
        runCancellationDemo();

        // Scenario 3: Real-World Distributed System Design Demo: 100k IoT Heartbeat Manager
        runHeartbeatManagerDemo();

        printSummaryTable();
    }

    private static void printBanner() {
        System.out.println("================================================================================");
        System.out.println("     HIERARCHICAL TIMING WHEEL (Hashed & Hierarchical Timing Wheels Demo)       ");
        System.out.println("         Reference Algorithm: Varghese & Lauck (1987) / Kafka Timer             ");
        System.out.println("================================================================================\n");
    }

    private static void runCascadeDemo() throws Exception {
        System.out.println("--- [SCENARIO 1] Multi-Tier Scheduling & Cascade Demonstration ---");
        System.out.println("Configuring SystemTimer with tick = 20ms, wheelSize = 10:");
        System.out.println("  * Layer 0: tick = 20ms,   span = 200ms");
        System.out.println("  * Layer 1: tick = 200ms,  span = 2,000ms");
        System.out.println("  * Layer 2: tick = 2,000ms, span = 20,000ms\n");

        CountDownLatch latch = new CountDownLatch(4);
        long startTime = System.currentTimeMillis();

        try (SystemTimer timer = new SystemTimer("cascade-demo", 20, 10)) {
            System.out.printf("[%s] Scheduling tasks across different time horizons...\n", LocalTime.now().format(TIME_FMT));

            timer.schedule(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("  -> [%s] Task A (50ms target) EXECUTED after %d ms (Tier 0)\n",
                        LocalTime.now().format(TIME_FMT), elapsed);
                latch.countDown();
            }, 50);

            timer.schedule(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("  -> [%s] Task B (180ms target) EXECUTED after %d ms (Tier 0)\n",
                        LocalTime.now().format(TIME_FMT), elapsed);
                latch.countDown();
            }, 180);

            timer.schedule(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("  -> [%s] Task C (500ms target) EXECUTED after %d ms (Cascaded Tier 1 -> Tier 0)\n",
                        LocalTime.now().format(TIME_FMT), elapsed);
                latch.countDown();
            }, 500);

            timer.schedule(() -> {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.printf("  -> [%s] Task D (1200ms target) EXECUTED after %d ms (Cascaded Tier 1 -> Tier 0)\n",
                        LocalTime.now().format(TIME_FMT), elapsed);
                latch.countDown();
            }, 1200);

            System.out.printf("Initial Hierarchy Depth spawned: %d tiers\n", timer.getHierarchyDepth());
            latch.await(3, TimeUnit.SECONDS);
            System.out.println("Scenario 1 completed successfully!\n");
        }
    }

    private static void runCancellationDemo() throws Exception {
        System.out.println("--- [SCENARIO 2] O(1) Task Cancellation Demonstration ---");
        CountDownLatch latch = new CountDownLatch(1);

        try (SystemTimer timer = new SystemTimer("cancel-demo", 20, 10)) {
            System.out.println("Scheduling Task 1 (300ms) and Task 2 (500ms)...");

            TimerTask task1 = timer.schedule(() -> {
                System.out.println("  [ERROR] Task 1 should have been cancelled!");
            }, 300);

            timer.schedule(() -> {
                System.out.printf("  -> [%s] Task 2 (500ms) executed as planned.\n", LocalTime.now().format(TIME_FMT));
                latch.countDown();
            }, 500);

            Thread.sleep(100);
            System.out.println("Cancelling Task 1 at 100ms mark...");
            boolean cancelled = task1.cancel();
            System.out.printf("  -> Task 1 cancel result: %s (isCancelled=%s, pending=%d)\n",
                    cancelled, task1.isCancelled(), timer.size());

            latch.await(2, TimeUnit.SECONDS);
            System.out.println("Scenario 2 completed!\n");
        }
    }

    private static void runHeartbeatManagerDemo() throws Exception {
        System.out.println("--- [SCENARIO 3] System Design: 100,000 IoT Connection Heartbeat Monitor ---");
        System.out.println("Simulating 100,000 connected TCP/WebSocket clients with 5000ms idle timeout...");

        int connectionCount = 100_000;
        CountDownLatch latch = new CountDownLatch(connectionCount);
        AtomicInteger expiredCount = new AtomicInteger(0);

        HierarchicalTimingWheel wheel = new HierarchicalTimingWheel(50, 60, task -> {
            expiredCount.incrementAndGet();
            latch.countDown();
        });

        Random random = new Random(12345);
        long startInsert = System.nanoTime();

        for (int i = 0; i < connectionCount; i++) {
            // Heartbeat deadline between 100ms and 4000ms
            long delay = 100 + random.nextInt(3900);
            TimerTask task = new TimerTask(delay) {
                @Override
                public void run() {}
            };
            wheel.add(task);
        }

        long insertDuration = System.nanoTime() - startInsert;
        System.out.printf("  * Inserted %d heartbeat monitors in %.2f ms (%.0f inserts/sec)\n",
                connectionCount, insertDuration / 1_000_000.0,
                (connectionCount / (insertDuration / 1_000_000_000.0)));
        System.out.printf("  * Total tiers generated: %d\n", wheel.getHierarchyDepth());
        System.out.printf("  * Active timer count: %d\n\n", wheel.size());
    }

    private static void printSummaryTable() {
        System.out.println("================================================================================");
        System.out.println("              ALGORITHMIC TIME COMPLEXITY COMPARISON                           ");
        System.out.println("================================================================================");
        System.out.println("| Algorithm / Facility         | Insert Task | Cancel Task | Advance Tick / Expire |");
        System.out.println("|------------------------------|-------------|-------------|-----------------------|");
        System.out.println("| Min-Heap (PriorityQueue)     | O(log N)    | O(N)        | O(log N)              |");
        System.out.println("| java.util.Timer              | O(log N)    | O(N)        | O(log N) (Single Thr) |");
        System.out.println("| ScheduledThreadPoolExecutor  | O(log N)    | O(N)        | O(log N)              |");
        System.out.println("| Single Hashed Timing Wheel   | O(1)        | O(1)        | O(1) / O(N_slot)      |");
        System.out.println("| Hierarchical Timing Wheel    | O(M) ≈ O(1) | O(1)        | O(1) + O(K_cascade)   |");
        System.out.println("================================================================================");
    }
}
