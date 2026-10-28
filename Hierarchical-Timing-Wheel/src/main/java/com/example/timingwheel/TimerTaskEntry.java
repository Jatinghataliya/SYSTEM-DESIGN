package com.example.timingwheel;

/**
 * Node element representing a {@link TimerTask} inside a {@link TimerTaskList} bucket.
 * <p>
 * Forms a doubly-linked list inside each bucket to allow O(1) task insertion and O(1) removal.
 */
public class TimerTaskEntry implements Comparable<TimerTaskEntry> {

    public final TimerTask timerTask;
    public final long expirationMs;

    // Package-private pointers for the doubly-linked list
    volatile TimerTaskList list = null;
    TimerTaskEntry next = null;
    TimerTaskEntry prev = null;

    public TimerTaskEntry(TimerTask timerTask, long expirationMs) {
        this.timerTask = timerTask;
        this.expirationMs = expirationMs;
        if (timerTask != null) {
            timerTask.setTimerTaskEntry(this);
        }
    }

    /**
     * Checks if this entry is currently attached to any bucket list.
     */
    public boolean cancelled() {
        return timerTask != null && timerTask.getTimerTaskEntry() != this;
    }

    /**
     * Removes this entry from its containing {@link TimerTaskList} bucket in O(1) time.
     */
    public void remove() {
        TimerTaskList currentList = list;
        // Keep attempting removal until this entry is completely detached
        while (currentList != null) {
            currentList.remove(this);
            currentList = list;
        }
    }

    @Override
    public int compareTo(TimerTaskEntry other) {
        return Long.compare(this.expirationMs, other.expirationMs);
    }

    @Override
    public String toString() {
        return "TimerTaskEntry(expirationMs=" + expirationMs + ", cancelled=" + cancelled() + ")";
    }
}
