package com.example.timingwheel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TimerTaskList (Doubly-Linked Bucket) Tests")
class TimerTaskListTest {

    @Test
    @DisplayName("Should add, count, and remove task entries in O(1)")
    void testAddAndRemove() {
        AtomicInteger counter = new AtomicInteger(0);
        TimerTaskList list = new TimerTaskList(counter);

        assertEquals(0, list.size());
        assertEquals(0, counter.get());

        TimerTask task1 = new DummyTask(100);
        TimerTaskEntry entry1 = new TimerTaskEntry(task1, 100);
        list.add(entry1);

        assertEquals(1, list.size());
        assertEquals(1, counter.get());
        assertSame(list, entry1.list);

        TimerTask task2 = new DummyTask(200);
        TimerTaskEntry entry2 = new TimerTaskEntry(task2, 200);
        list.add(entry2);

        assertEquals(2, list.size());
        assertEquals(2, counter.get());

        // Remove entry1
        entry1.remove();
        assertEquals(1, list.size());
        assertEquals(1, counter.get());
        assertNull(entry1.list);

        // Remove entry2
        entry2.remove();
        assertEquals(0, list.size());
        assertEquals(0, counter.get());
        assertNull(entry2.list);
    }

    @Test
    @DisplayName("Should flush all entries and reset expiration")
    void testFlush() {
        AtomicInteger counter = new AtomicInteger(0);
        TimerTaskList list = new TimerTaskList(counter);
        list.setExpiration(5000);

        List<TimerTaskEntry> flushed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TimerTask task = new DummyTask(i * 10);
            TimerTaskEntry entry = new TimerTaskEntry(task, i * 10);
            list.add(entry);
        }

        assertEquals(5, list.size());
        assertEquals(5, counter.get());

        list.flush(flushed::add);

        assertEquals(0, list.size());
        assertEquals(0, counter.get());
        assertEquals(5, flushed.size());
        assertEquals(-1L, list.getExpiration());
    }

    @Test
    @DisplayName("Task cancel() should detach entry from bucket")
    void testTaskCancelDetachesEntry() {
        AtomicInteger counter = new AtomicInteger(0);
        TimerTaskList list = new TimerTaskList(counter);

        TimerTask task = new DummyTask(1000);
        TimerTaskEntry entry = new TimerTaskEntry(task, 1000);
        list.add(entry);

        assertEquals(1, list.size());
        assertFalse(task.isCancelled());

        boolean cancelled = task.cancel();
        assertTrue(cancelled);
        assertTrue(task.isCancelled());
        assertEquals(0, list.size());
        assertEquals(0, counter.get());
    }

    private static class DummyTask extends TimerTask {
        public DummyTask(long delayMs) {
            super(delayMs);
        }

        @Override
        public void run() {
            // No-op for bucket structure tests
        }
    }
}
