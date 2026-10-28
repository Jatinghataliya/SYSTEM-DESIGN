package com.example.reminder.wheel;

/**
 * Node element representing a {@link TimerTask} inside a {@link TimerTaskList} bucket.
 */
public class TimerTaskEntry implements Comparable<TimerTaskEntry> {

    public final TimerTask timerTask;
    public final long expirationMs;

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

    public boolean cancelled() {
        return timerTask != null && timerTask.getTimerTaskEntry() != this;
    }

    public void remove() {
        TimerTaskList currentList = list;
        while (currentList != null) {
            currentList.remove(this);
            currentList = list;
        }
    }

    @Override
    public int compareTo(TimerTaskEntry other) {
        return Long.compare(this.expirationMs, other.expirationMs);
    }
}
