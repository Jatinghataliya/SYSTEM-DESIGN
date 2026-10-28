package com.example.timingwheel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimingWheel Multi-Level Tier Tests")
class TimingWheelTest {

    @Test
    @DisplayName("Should create single wheel and place tasks in correct bucket slots")
    void testSingleWheelPlacement() {
        AtomicInteger counter = new AtomicInteger(0);
        DelayQueue<TimerTaskList> queue = new DelayQueue<>();
        long startMs = 1000L;

        // tickMs = 10ms, wheelSize = 20 -> interval = 200ms
        TimingWheel wheel = new TimingWheel(10, 20, startMs, counter, queue);

        assertEquals(10, wheel.tickMs);
        assertEquals(20, wheel.wheelSize);
        assertEquals(200, wheel.interval);
        assertEquals(1000L, wheel.getCurrentTimeMs());

        // Schedule task at startMs + 50ms (bucket 5)
        TimerTask task1 = new DummyTask(50);
        TimerTaskEntry entry1 = new TimerTaskEntry(task1, 1050L);
        boolean added1 = wheel.add(entry1);
        assertTrue(added1);
        assertEquals(1, counter.get());
        assertEquals(1, queue.size());

        // Schedule task at startMs + 150ms (bucket 15)
        TimerTask task2 = new DummyTask(150);
        TimerTaskEntry entry2 = new TimerTaskEntry(task2, 1150L);
        boolean added2 = wheel.add(entry2);
        assertTrue(added2);
        assertEquals(2, counter.get());
        assertEquals(2, queue.size());
    }

    @Test
    @DisplayName("Should lazily create overflow wheel when task exceeds tier interval")
    void testOverflowWheelCreation() {
        AtomicInteger counter = new AtomicInteger(0);
        DelayQueue<TimerTaskList> queue = new DelayQueue<>();
        long startMs = 1000L;

        // Level 0: tickMs = 10ms, wheelSize = 20 -> interval = 200ms
        TimingWheel wheel = new TimingWheel(10, 20, startMs, counter, queue);
        assertNull(wheel.getOverflowWheelIfExists());
        assertEquals(1, wheel.getHierarchyDepth());

        // Add task with delay 500ms -> exceeds Level 0 (200ms), fits in Level 1 (tick=200ms, interval=4000ms)
        TimerTask task = new DummyTask(500);
        TimerTaskEntry entry = new TimerTaskEntry(task, 1500L);
        boolean added = wheel.add(entry);

        assertTrue(added);
        assertNotNull(wheel.getOverflowWheelIfExists());
        assertEquals(2, wheel.getHierarchyDepth());
        assertEquals(200, wheel.getOverflowWheelIfExists().tickMs);
        assertEquals(4000, wheel.getOverflowWheelIfExists().interval);
    }

    @Test
    @DisplayName("Should advance clock and propagate to upper tiers")
    void testAdvanceClock() {
        AtomicInteger counter = new AtomicInteger(0);
        DelayQueue<TimerTaskList> queue = new DelayQueue<>();
        long startMs = 1000L;

        TimingWheel wheel = new TimingWheel(10, 20, startMs, counter, queue);

        // Schedule task in Level 1 (e.g. 500ms delay)
        TimerTask task = new DummyTask(500);
        TimerTaskEntry entry = new TimerTaskEntry(task, 1500L);
        wheel.add(entry);

        // Advance root clock to 1250ms
        wheel.advanceClock(1250L);
        assertEquals(1250L, wheel.getCurrentTimeMs());
        assertEquals(1200L, wheel.getOverflowWheelIfExists().getCurrentTimeMs());
    }

    private static class DummyTask extends TimerTask {
        public DummyTask(long delayMs) {
            super(delayMs);
        }

        @Override
        public void run() {}
    }
}
