package com.example.timingwheel;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A single-tier timing wheel with a fixed number of slots (buckets) and tick resolution.
 * <p>
 * If a task's deadline exceeds this wheel's total span ({@code interval = tickMs * wheelSize}),
 * the task is delegated to an overflow timing wheel with larger tick resolution ({@code overflowWheel.tickMs = interval}).
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

    /**
     * Adds a task entry to the appropriate bucket in this wheel or an overflow wheel.
     *
     * @param timerTaskEntry the task entry to add
     * @return true if the task was placed into a bucket; false if it has already expired relative to current tick
     */
    public boolean add(TimerTaskEntry timerTaskEntry) {
        long expirationMs = timerTaskEntry.expirationMs;

        if (timerTaskEntry.cancelled()) {
            return false;
        } else if (expirationMs < currentTimeMs + tickMs) {
            // Already expired or falls within current tick
            return false;
        } else if (expirationMs < currentTimeMs + interval) {
            // Fits within the current wheel span
            long virtualId = expirationMs / tickMs;
            int bucketIndex = (int) (virtualId % wheelSize);
            TimerTaskList bucket = buckets[bucketIndex];
            bucket.add(timerTaskEntry);

            // Set the bucket expiration and register with DelayQueue if first entry in this cycle
            if (bucket.setExpiration(virtualId * tickMs)) {
                delayQueue.offer(bucket);
            }
            return true;
        } else {
            // Exceeds this wheel's horizon, delegate to upper overflow tier
            return getOverflowWheel().add(timerTaskEntry);
        }
    }

    /**
     * Advances the clock of this wheel and recursively propagates to overflow wheels.
     *
     * @param timeMs the new current time in milliseconds
     */
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

    public int getWheelSize() {
        return wheelSize;
    }

    public TimingWheel getOverflowWheelIfExists() {
        return overflowWheel;
    }

    /**
     * Counts how many hierarchical tiers have been spawned.
     */
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
