package com.example.reminder.wheel;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-tier timing wheel with dynamic overflow tier generation.
 */
public class TimingWheel {

    public final long tickMs;
    public final int wheelSize;
    public final long interval;
    public final long startMs;

    private final AtomicInteger taskCounter;
    private final DelayQueue<TimerTaskList> delayQueue;
    private final TimerTaskList[] buckets;

    private volatile long currentTimeMs;
    private volatile TimingWheel overflowWheel = null;

    public TimingWheel(long tickMs, int wheelSize, long startMs, AtomicInteger taskCounter, DelayQueue<TimerTaskList> delayQueue) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.startMs = startMs;
        this.taskCounter = taskCounter;
        this.delayQueue = delayQueue;
        this.interval = tickMs * wheelSize;
        this.currentTimeMs = startMs - (startMs % tickMs);

        this.buckets = new TimerTaskList[wheelSize];
        for (int i = 0; i < wheelSize; i++) {
            this.buckets[i] = new TimerTaskList(taskCounter);
        }
    }

    private synchronized TimingWheel getOverflowWheel() {
        if (overflowWheel == null) {
            overflowWheel = new TimingWheel(
                    interval,
                    wheelSize,
                    currentTimeMs,
                    taskCounter,
                    delayQueue
            );
        }
        return overflowWheel;
    }

    public boolean add(TimerTaskEntry timerTaskEntry) {
        long expirationMs = timerTaskEntry.expirationMs;

        if (timerTaskEntry.cancelled()) {
            return false;
        } else if (expirationMs < currentTimeMs + tickMs) {
            return false;
        } else if (expirationMs < currentTimeMs + interval) {
            long virtualId = expirationMs / tickMs;
            int bucketIndex = (int) (virtualId % wheelSize);
            TimerTaskList bucket = buckets[bucketIndex];
            bucket.add(timerTaskEntry);

            if (bucket.setExpiration(virtualId * tickMs)) {
                delayQueue.offer(bucket);
            }
            return true;
        } else {
            return getOverflowWheel().add(timerTaskEntry);
        }
    }

    public void advanceClock(long timeMs) {
        if (timeMs >= currentTimeMs + tickMs) {
            currentTimeMs = timeMs - (timeMs % tickMs);
            if (overflowWheel != null) {
                overflowWheel.advanceClock(currentTimeMs);
            }
        }
    }

    public long getCurrentTimeMs() {
        return currentTimeMs;
    }

    public int getHierarchyDepth() {
        int depth = 1;
        TimingWheel curr = overflowWheel;
        while (curr != null) {
            depth++;
            curr = curr.overflowWheel;
        }
        return depth;
    }
}
