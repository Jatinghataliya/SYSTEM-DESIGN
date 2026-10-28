package com.example.timingwheel;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A thread-safe, circular doubly-linked list bucket in a {@link TimingWheel}.
 * <p>
 * Implements {@link Delayed} so that non-empty buckets can be enqueued into a
 * {@link java.util.concurrent.DelayQueue} for efficient, interruptible bucket expiration
 * without busy-spinning over empty wheel slots.
 */
public class TimerTaskList implements Delayed {

    // Sentinel root node for the circular doubly-linked list
    private final TimerTaskEntry root = new TimerTaskEntry(null, -1);
    private final AtomicLong expiration = new AtomicLong(-1L);
    private final AtomicInteger taskCounter;

    public TimerTaskList(AtomicInteger taskCounter) {
        this.taskCounter = taskCounter;
        synchronized (this) {
            root.next = root;
            root.prev = root;
        }
    }

    /**
     * Sets the bucket expiration timestamp.
     *
     * @param expirationMs the new expiration timestamp in milliseconds
     * @return true if the expiration was changed from -1 or updated
     */
    public boolean setExpiration(long expirationMs) {
        return expiration.getAndSet(expirationMs) != expirationMs;
    }

    public long getExpiration() {
        return expiration.get();
    }

    /**
     * Adds an entry to the end of this doubly-linked list bucket.
     *
     * @param entry the entry to append
     */
    public synchronized void add(TimerTaskEntry entry) {
        boolean done = false;
        while (!done) {
            // Remove from any prior list first
            entry.remove();

            synchronized (this) {
                if (entry.list == null) {
                    TimerTaskEntry tail = root.prev;
                    entry.next = root;
                    entry.prev = tail;
                    entry.list = this;
                    tail.next = entry;
                    root.prev = entry;
                    if (taskCounter != null) {
                        taskCounter.incrementAndGet();
                    }
                    done = true;
                }
            }
        }
    }

    /**
     * Removes an entry from this list in O(1) time.
     *
     * @param entry the entry to remove
     */
    public synchronized void remove(TimerTaskEntry entry) {
        synchronized (this) {
            if (entry.list == this) {
                entry.next.prev = entry.prev;
                entry.prev.next = entry.next;
                entry.next = null;
                entry.prev = null;
                entry.list = null;
                if (taskCounter != null) {
                    taskCounter.decrementAndGet();
                }
            }
        }
    }

    /**
     * Removes all elements from this list and applies the given action to each entry.
     * Resets expiration to -1.
     *
     * @param consumer action to execute for each unlinked entry
     */
    public synchronized void flush(Consumer<TimerTaskEntry> consumer) {
        TimerTaskEntry head = root.next;
        while (head != root) {
            TimerTaskEntry curr = head;
            head = head.next;
            remove(curr);
            consumer.accept(curr);
        }
        expiration.set(-1L);
    }

    public synchronized int size() {
        int count = 0;
        TimerTaskEntry curr = root.next;
        while (curr != root) {
            count++;
            curr = curr.next;
        }
        return count;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long delay = Math.max(0, expiration.get() - System.currentTimeMillis());
        return unit.convert(delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        if (other instanceof TimerTaskList otherList) {
            return Long.compare(this.expiration.get(), otherList.expiration.get());
        }
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
    }
}
