package com.example.timingwheel;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Hierarchical Timing Wheel timer engine based on the Varghese and Lauck algorithm,
 * enhanced with Kafka-style {@link DelayQueue} bucket management.
 * <p>
 * Supports arbitrary task timeouts ranging from milliseconds to days across multiple
 * wheel tiers while maintaining O(1) task scheduling and cancellation.
 */
public class HierarchicalTimingWheel {

    private final long tickMs;
    private final int wheelSize;
    private final long startMs;
    private final DelayQueue<TimerTaskList> delayQueue;
    private final AtomicInteger taskCounter;
    private final TimingWheel rootWheel;

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();

    private final Consumer<TimerTask> expiredTaskConsumer;

    public HierarchicalTimingWheel(long tickMs, int wheelSize, Consumer<TimerTask> expiredTaskConsumer) {
        this(tickMs, wheelSize, System.currentTimeMillis(), expiredTaskConsumer);
    }

    public HierarchicalTimingWheel(long tickMs, int wheelSize, long startMs, Consumer<TimerTask> expiredTaskConsumer) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.startMs = startMs;
        this.expiredTaskConsumer = expiredTaskConsumer;
        this.delayQueue = new DelayQueue<>();
        this.taskCounter = new AtomicInteger(0);
        this.rootWheel = new TimingWheel(tickMs, wheelSize, startMs, taskCounter, delayQueue);
    }

    /**
     * Adds a task to the hierarchical timing wheel.
     *
     * @param timerTask the task to be scheduled
     */
    public void add(TimerTask timerTask) {
        readLock.lock();
        try {
            long expirationMs = System.currentTimeMillis() + timerTask.delayMs;
            TimerTaskEntry entry = new TimerTaskEntry(timerTask, expirationMs);

            if (!rootWheel.add(entry)) {
                // Task has already expired or cannot be added to any bucket
                if (!timerTask.isCancelled()) {
                    executeExpiredTask(timerTask);
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Advances the timing wheel clock by polling expired buckets from the delay queue.
     *
     * @param timeoutMs maximum time to wait for an expired bucket in milliseconds
     * @return true if at least one bucket expired and was processed; false otherwise
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean advanceClock(long timeoutMs) throws InterruptedException {
        TimerTaskList bucket = delayQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (bucket != null) {
            writeLock.lock();
            try {
                while (bucket != null) {
                    rootWheel.advanceClock(bucket.getExpiration());
                    bucket.flush(this::reinsertOrExecute);
                    bucket = delayQueue.poll();
                }
            } finally {
                writeLock.unlock();
            }
            return true;
        } else {
            // No bucket expired within timeout, update clock conservatively to current time
            writeLock.lock();
            try {
                rootWheel.advanceClock(System.currentTimeMillis());
            } finally {
                writeLock.unlock();
            }
            return false;
        }
    }

    private void reinsertOrExecute(TimerTaskEntry entry) {
        if (!rootWheel.add(entry)) {
            // When re-inserting into lower wheel layers, if task falls within current tick, execute it
            TimerTask task = entry.timerTask;
            if (task != null && !task.isCancelled()) {
                executeExpiredTask(task);
            }
        }
    }

    private void executeExpiredTask(TimerTask task) {
        if (expiredTaskConsumer != null) {
            expiredTaskConsumer.accept(task);
        } else {
            task.run();
        }
    }

    public int size() {
        return taskCounter.get();
    }

    public int getHierarchyDepth() {
        readLock.lock();
        try {
            return rootWheel.getHierarchyDepth();
        } finally {
            readLock.unlock();
        }
    }

    public long getRootTickMs() {
        return tickMs;
    }

    public int getWheelSize() {
        return wheelSize;
    }

    public TimingWheel getRootWheel() {
        return rootWheel;
    }
}
