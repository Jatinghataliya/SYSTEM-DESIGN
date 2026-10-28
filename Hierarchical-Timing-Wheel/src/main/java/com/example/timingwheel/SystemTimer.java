package com.example.timingwheel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-level production timer service wrapping {@link HierarchicalTimingWheel}.
 * <p>
 * Manages an internal tick-driver background thread to advance the wheel clock,
 * and a worker thread pool to asynchronously execute expired callbacks.
 */
public class SystemTimer implements AutoCloseable {

    private final HierarchicalTimingWheel timingWheel;
    private final ExecutorService workerExecutor;
    private final Thread tickThread;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public SystemTimer(String name, long tickMs, int wheelSize) {
        this.workerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, name + "-worker");
            t.setDaemon(true);
            return t;
        });

        this.timingWheel = new HierarchicalTimingWheel(
                tickMs,
                wheelSize,
                task -> workerExecutor.submit(task)
        );

        this.tickThread = new Thread(() -> {
            while (running.get()) {
                try {
                    timingWheel.advanceClock(200);
                } catch (InterruptedException e) {
                    if (!running.get()) {
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, name + "-tick-driver");
        this.tickThread.setDaemon(true);
        this.tickThread.start();
    }

    /**
     * Schedules a {@link TimerTask} for execution after its delay.
     */
    public void schedule(TimerTask task) {
        if (!running.get()) {
            throw new IllegalStateException("SystemTimer has been shut down");
        }
        timingWheel.add(task);
    }

    /**
     * Schedules a {@link Runnable} after a specified delay.
     *
     * @param action  the callback action
     * @param delayMs delay in milliseconds
     * @return the scheduled {@link TimerTask} for potential cancellation
     */
    public TimerTask schedule(Runnable action, long delayMs) {
        TimerTask task = new TimerTask(delayMs) {
            @Override
            public void run() {
                action.run();
            }
        };
        schedule(task);
        return task;
    }

    /**
     * Returns total pending tasks in the timer.
     */
    public int size() {
        return timingWheel.size();
    }

    /**
     * Returns current depth of wheel hierarchy.
     */
    public int getHierarchyDepth() {
        return timingWheel.getHierarchyDepth();
    }

    public HierarchicalTimingWheel getTimingWheel() {
        return timingWheel;
    }

    /**
     * Shuts down the timer service and releases worker threads.
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            tickThread.interrupt();
            workerExecutor.shutdown();
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return workerExecutor.awaitTermination(timeout, unit);
    }

    @Override
    public void close() {
        shutdown();
    }
}
