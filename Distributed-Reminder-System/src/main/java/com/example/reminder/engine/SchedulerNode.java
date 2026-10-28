package com.example.reminder.engine;

import com.example.reminder.model.DispatchEvent;
import com.example.reminder.model.Reminder;
import com.example.reminder.queue.NotificationQueue;
import com.example.reminder.wheel.HierarchicalTimingWheel;
import com.example.reminder.wheel.TimerTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An individual scheduler worker node running an in-memory {@link HierarchicalTimingWheel}.
 */
public class SchedulerNode implements AutoCloseable {

    private final String nodeId;
    private final NotificationQueue notificationQueue;
    private final HierarchicalTimingWheel timingWheel;
    private final Map<String, TimerTask> activeTasks = new ConcurrentHashMap<>();

    private final Thread tickThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SchedulerNode(String nodeId, NotificationQueue notificationQueue, long tickMs, int wheelSize) {
        this.nodeId = nodeId;
        this.notificationQueue = notificationQueue;
        this.timingWheel = new HierarchicalTimingWheel(
                tickMs,
                wheelSize,
                TimerTask::run
        );

        this.tickThread = new Thread(() -> {
            while (running.get()) {
                try {
                    timingWheel.advanceClock(50);
                } catch (InterruptedException e) {
                    if (!running.get()) {
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, nodeId + "-wheel-driver");
        this.tickThread.setDaemon(true);
        this.tickThread.start();
    }

    /**
     * Schedules a reminder in the node's in-memory hierarchical timing wheel.
     */
    public void schedule(Reminder reminder) {
        long delayMs = Math.max(0, reminder.getTriggerTimeMs() - System.currentTimeMillis());

        TimerTask task = new TimerTask(delayMs) {
            @Override
            public void run() {
                activeTasks.remove(reminder.getId());
                DispatchEvent event = DispatchEvent.fromReminder(reminder);
                notificationQueue.publish(event);
            }
        };

        activeTasks.put(reminder.getId(), task);
        timingWheel.add(task);
    }

    /**
     * Cancels an in-flight scheduled reminder in O(1) time.
     */
    public boolean cancel(String reminderId) {
        TimerTask task = activeTasks.remove(reminderId);
        if (task != null) {
            return task.cancel();
        }
        return false;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    public HierarchicalTimingWheel getTimingWheel() {
        return timingWheel;
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            tickThread.interrupt();
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}
