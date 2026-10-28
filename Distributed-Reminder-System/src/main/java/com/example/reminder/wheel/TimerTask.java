package com.example.reminder.wheel;

/**
 * Base abstract class for tasks scheduled in a {@link HierarchicalTimingWheel}.
 */
public abstract class TimerTask implements Runnable {

    public final long delayMs;
    private TimerTaskEntry timerTaskEntry = null;

    public TimerTask(long delayMs) {
        this.delayMs = delayMs;
    }

    public synchronized boolean cancel() {
        if (timerTaskEntry != null) {
            timerTaskEntry.remove();
            timerTaskEntry = null;
            return true;
        }
        return false;
    }

    public synchronized boolean isCancelled() {
        return timerTaskEntry == null;
    }

    public synchronized void setTimerTaskEntry(TimerTaskEntry entry) {
        if (this.timerTaskEntry != null && this.timerTaskEntry != entry) {
            this.timerTaskEntry.remove();
        }
        this.timerTaskEntry = entry;
    }

    public synchronized TimerTaskEntry getTimerTaskEntry() {
        return timerTaskEntry;
    }
}
