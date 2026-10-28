package com.example.timingwheel;

/**
 * Base abstract class for tasks scheduled in a {@link HierarchicalTimingWheel}.
 * <p>
 * A task holds its relative delay, deadline timestamp, cancellation state,
 * and the callback execution logic.
 */
public abstract class TimerTask implements Runnable {

    /**
     * Delay in milliseconds before this task should execute.
     */
    public final long delayMs;

    /**
     * Reference to the entry in the timing wheel's doubly-linked list.
     * Guarded by synchronization within TimerTask and TimerTaskList.
     */
    private TimerTaskEntry timerTaskEntry = null;

    public TimerTask(long delayMs) {
        this.delayMs = delayMs;
    }

    /**
     * Cancels this task. If it has not yet run, it will be removed from the timing wheel.
     *
     * @return true if successfully cancelled before execution; false if already cancelled or executed.
     */
    public synchronized boolean cancel() {
        if (timerTaskEntry != null) {
            timerTaskEntry.remove();
            timerTaskEntry = null;
            return true;
        }
        return false;
    }

    /**
     * Checks if this task was cancelled.
     */
    public synchronized boolean isCancelled() {
        return timerTaskEntry == null;
    }

    public synchronized void setTimerTaskEntry(TimerTaskEntry entry) {
        // If this timer task is already scheduled in another entry, remove the old one first
        if (this.timerTaskEntry != null && this.timerTaskEntry != entry) {
            this.timerTaskEntry.remove();
        }
        this.timerTaskEntry = entry;
    }

    public synchronized TimerTaskEntry getTimerTaskEntry() {
        return timerTaskEntry;
    }
}
