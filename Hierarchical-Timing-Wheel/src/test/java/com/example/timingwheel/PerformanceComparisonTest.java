package com.example.timingwheel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.PriorityQueue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Performance & Complexity Benchmark Tests")
class PerformanceComparisonTest {

    @Test
    @DisplayName("Hierarchical Timing Wheel O(1) insert vs PriorityQueue O(log N) insert benchmark")
    void testInsertThroughputComparison() {
        int taskCount = 50_000;
        Random random = new Random(42);
        long[] delays = new long[taskCount];
        for (int i = 0; i < taskCount; i++) {
            delays[i] = 10L + random.nextInt(60_000);
        }

        // 1. Benchmark PriorityQueue (Min-Heap)
        PriorityQueue<Long> minHeap = new PriorityQueue<>();
        long startHeap = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            minHeap.offer(delays[i]);
        }
        long heapDurationNs = System.nanoTime() - startHeap;

        // 2. Benchmark Hierarchical Timing Wheel
        HierarchicalTimingWheel wheel = new HierarchicalTimingWheel(10, 20, task -> {});
        TimerTask[] tasks = new TimerTask[taskCount];
        for (int i = 0; i < taskCount; i++) {
            final long d = delays[i];
            tasks[i] = new TimerTask(d) {
                @Override
                public void run() {}
            };
        }

        long startWheel = System.nanoTime();
        for (int i = 0; i < taskCount; i++) {
            wheel.add(tasks[i]);
        }
        long wheelDurationNs = System.nanoTime() - startWheel;

        System.out.printf("[BENCHMARK] Inserting %d tasks:\n", taskCount);
        System.out.printf("  - Min-Heap PriorityQueue: %.2f ms (%.0f ops/sec)\n",
                heapDurationNs / 1_000_000.0, (taskCount / (heapDurationNs / 1_000_000_000.0)));
        System.out.printf("  - Hierarchical Timing Wheel: %.2f ms (%.0f ops/sec)\n",
                wheelDurationNs / 1_000_000.0, (taskCount / (wheelDurationNs / 1_000_000_000.0)));
        System.out.printf("  - Wheel Hierarchy Depth spawned: %d tiers\n", wheel.getHierarchyDepth());

        // 3. Benchmark O(1) Cancellation
        long startCancel = System.nanoTime();
        int cancelledCount = 0;
        for (int i = 0; i < taskCount; i += 2) {
            if (tasks[i].cancel()) {
                cancelledCount++;
            }
        }
        long cancelDurationNs = System.nanoTime() - startCancel;
        System.out.printf("  - Timing Wheel O(1) Cancel of %d tasks: %.2f ms (%.0f ops/sec)\n",
                cancelledCount, cancelDurationNs / 1_000_000.0, (cancelledCount / (cancelDurationNs / 1_000_000_000.0)));

        assertTrue(wheel.size() > 0);
    }
}
