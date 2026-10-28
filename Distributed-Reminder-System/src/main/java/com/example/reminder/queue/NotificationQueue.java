package com.example.reminder.queue;

import com.example.reminder.model.DispatchEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput in-memory message queue simulating a distributed broker (e.g., Apache Kafka / RabbitMQ).
 * <p>
 * Decouples the timing wheel expiration from downstream push notification delivery.
 */
public class NotificationQueue {

    private final BlockingQueue<DispatchEvent> queue = new LinkedBlockingQueue<>();
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong consumedCount = new AtomicLong(0);

    /**
     * Publishes a dispatch event into the queue in sub-microsecond time.
     */
    public boolean publish(DispatchEvent event) {
        publishedCount.incrementAndGet();
        return queue.offer(event);
    }

    /**
     * Polls a single event from the queue with timeout.
     */
    public DispatchEvent poll(long timeoutMs) throws InterruptedException {
        DispatchEvent event = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (event != null) {
            consumedCount.incrementAndGet();
        }
        return event;
    }

    /**
     * Drains available events up to maxBatchSize for batch consumer processing.
     */
    public List<DispatchEvent> drainBatch(int maxBatchSize) {
        List<DispatchEvent> batch = new ArrayList<>(maxBatchSize);
        queue.drainTo(batch, maxBatchSize);
        consumedCount.addAndGet(batch.size());
        return batch;
    }

    public int size() {
        return queue.size();
    }

    public long getPublishedCount() {
        return publishedCount.get();
    }

    public long getConsumedCount() {
        return consumedCount.get();
    }
}
