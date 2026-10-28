package com.example.timingwheel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HierarchicalTimingWheel Execution & Cascade Tests")
class HierarchicalTimingWheelTest {

    @Test
    @DisplayName("Tasks scheduled across multiple tiers should cascade and execute accurately")
    void testMultiTierCascading() throws Exception {
        List<String> executed = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(4);

        // tickMs = 20ms, wheelSize = 10 -> L0 = 200ms, L1 = 2000ms, L2 = 20000ms
        try (SystemTimer timer = new SystemTimer("test-timer", 20, 10)) {
            // Task 1: Level 0 (40ms)
            timer.schedule(() -> {
                executed.add("task-40ms");
                latch.countDown();
            }, 40);

            // Task 2: Level 0 boundary (180ms)
            timer.schedule(() -> {
                executed.add("task-180ms");
                latch.countDown();
            }, 180);

            // Task 3: Level 1 (350ms)
            timer.schedule(() -> {
                executed.add("task-350ms");
                latch.countDown();
            }, 350);

            // Task 4: Level 1 upper (600ms)
            timer.schedule(() -> {
                executed.add("task-600ms");
                latch.countDown();
            }, 600);

            assertTrue(latch.await(3, TimeUnit.SECONDS), "All tasks across tiers should execute within deadline");
            assertEquals(4, executed.size());
            assertEquals("task-40ms", executed.get(0));
            assertEquals("task-180ms", executed.get(1));
            assertEquals("task-350ms", executed.get(2));
            assertEquals("task-600ms", executed.get(3));
        }
    }

    @Test
    @DisplayName("Cancelled task should not execute even if its slot expires")
    void testTaskCancellation() throws Exception {
        AtomicBoolean ran1 = new AtomicBoolean(false);
        AtomicBoolean ran2 = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        try (SystemTimer timer = new SystemTimer("cancel-timer", 10, 20)) {
            TimerTask taskToCancel = timer.schedule(() -> ran1.set(true), 150);
            timer.schedule(() -> {
                ran2.set(true);
                latch.countDown();
            }, 250);

            // Cancel the first task before it fires
            Thread.sleep(30);
            boolean cancelled = taskToCancel.cancel();
            assertTrue(cancelled);
            assertTrue(taskToCancel.isCancelled());

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertFalse(ran1.get(), "Cancelled task should never run");
            assertTrue(ran2.get(), "Uncancelled task should execute");
        }
    }

    @Test
    @DisplayName("Concurrent scheduling from multiple threads should maintain thread-safety and count")
    void testConcurrentScheduling() throws Exception {
        int threadCount = 8;
        int tasksPerThread = 250;
        int totalTasks = threadCount * tasksPerThread;

        CountDownLatch latch = new CountDownLatch(totalTasks);
        AtomicInteger executedCount = new AtomicInteger(0);

        try (SystemTimer timer = new SystemTimer("concurrent-timer", 10, 20)) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    for (int i = 0; i < tasksPerThread; i++) {
                        long delay = 20 + (threadId * 10) + (i % 50);
                        timer.schedule(() -> {
                            executedCount.incrementAndGet();
                            latch.countDown();
                        }, delay);
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
            assertTrue(latch.await(5, TimeUnit.SECONDS), "All concurrent tasks should complete");
            assertEquals(totalTasks, executedCount.get());
        }
    }
}
