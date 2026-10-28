package com.example.reminder.wheel;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Multi-tier Hierarchical Timing Wheel engine for scheduling reminder tasks in O(1) time.
 */
public class HierarchicalTimingWheel {

    private final long tickMs;
    private final int wheelSize;
    private final DelayQueue<TimerTaskList> delayQueue;
    private final AtomicInteger taskCounter;
    private final TimingWheel rootWheel;

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();

    private final Consumer<TimerTask> expiredTaskConsumer;

    public HierarchicalTimingWheel(long tickMs, int wheelSize, Consumer<TimerTask> expiredTaskConsumer) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.expiredTaskConsumer = expiredTaskConsumer;
        this.delayQueue = new DelayQueue<>();
        this.taskCounter = new AtomicInteger(0);
        this.rootWheel = new TimingWheel(tickMs, wheelSize, System.currentTimeMillis(), taskCounter, delayQueue);
    }

    public void add(TimerTask timerTask) {
        readLock.lock();
        try {
            long expirationMs = System.currentTimeMillis() + timerTask.delayMs;
            TimerTaskEntry entry = new TimerTaskEntry(timerTask, expirationMs);

            if (!rootWheel.add(entry)) {
                if (!timerTask.isCancelled()) {
                    executeExpiredTask(timerTask);
                }
            }
        } finally {
            readLock.unlock();
        }
    }

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
}
