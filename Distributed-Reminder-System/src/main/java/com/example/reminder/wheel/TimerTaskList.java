package com.example.reminder.wheel;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe circular doubly-linked bucket list implementing {@link Delayed}.
 */
public class TimerTaskList implements Delayed {

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

    public boolean setExpiration(long expirationMs) {
        return expiration.getAndSet(expirationMs) != expirationMs;
    }

    public long getExpiration() {
        return expiration.get();
    }

    public synchronized void add(TimerTaskEntry entry) {
        boolean done = false;
        while (!done) {
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
