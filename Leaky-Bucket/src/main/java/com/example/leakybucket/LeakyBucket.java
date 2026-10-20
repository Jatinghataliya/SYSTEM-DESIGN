package com.example.leakybucket;

import java.time.Instant;

/**
 * Leaky Bucket Rate Limiter.
 *
 * <p>Incoming requests are added to a fixed-capacity bucket. The bucket leaks
 * (processes) requests at a constant rate. If the bucket is full when a new
 * request arrives the request is rejected (dropped), smoothing bursts into a
 * steady output stream.
 *
 * <pre>
 *  Requests arrive (possibly bursty)
 *        │
 *        ▼
 *  ┌──────────┐  capacity = N
 *  │  BUCKET  │  ◄─── drop if full
 *  └────┬─────┘
 *       │  leak rate = R requests / second
 *       ▼
 *  Outgoing stream (smooth)
 * </pre>
 *
 * Thread-safety: all public methods are {@code synchronized}.
 */
public class LeakyBucket {

    /** Maximum number of requests the bucket can hold. */
    private final int capacity;

    /** Number of requests allowed to leak (be processed) per second. */
    private final double leakRatePerSecond;

    /** Current water level (queued requests). */
    private double currentWater;

    /** Timestamp of the last leak calculation (epoch millis). */
    private long lastLeakTimestampMs;

    /** Total requests accepted into the bucket. */
    private long totalAccepted;

    /** Total requests dropped because the bucket was full. */
    private long totalDropped;

    /**
     * Creates a LeakyBucket.
     *
     * @param capacity          maximum number of requests the bucket can hold (≥ 1)
     * @param leakRatePerSecond requests processed per second (> 0)
     */
    public LeakyBucket(int capacity, double leakRatePerSecond) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        if (leakRatePerSecond <= 0) throw new IllegalArgumentException("leakRatePerSecond must be > 0");
        this.capacity = capacity;
        this.leakRatePerSecond = leakRatePerSecond;
        this.currentWater = 0;
        this.lastLeakTimestampMs = now();
    }

    /**
     * Attempts to add one request to the bucket.
     *
     * @return {@code true} if the request was accepted; {@code false} if dropped
     */
    public synchronized boolean tryConsume() {
        leak();
        if (currentWater + 1 <= capacity) {
            currentWater += 1;
            totalAccepted++;
            return true;
        }
        totalDropped++;
        return false;
    }

    /** Returns the current water level (number of queued requests). */
    public synchronized double getCurrentWater() {
        leak();
        return currentWater;
    }

    /** Returns the bucket capacity. */
    public int getCapacity() { return capacity; }

    /** Returns the leak rate in requests per second. */
    public double getLeakRatePerSecond() { return leakRatePerSecond; }

    /** Returns the total number of requests accepted since creation. */
    public synchronized long getTotalAccepted() { return totalAccepted; }

    /** Returns the total number of requests dropped since creation. */
    public synchronized long getTotalDropped() { return totalDropped; }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Leaks water proportional to the elapsed time since the last call.
     * Must be called while holding the monitor (i.e. from a synchronized context).
     */
    private void leak() {
        long nowMs = now();
        double elapsedSeconds = (nowMs - lastLeakTimestampMs) / 1000.0;
        double leaked = elapsedSeconds * leakRatePerSecond;
        currentWater = Math.max(0, currentWater - leaked);
        lastLeakTimestampMs = nowMs;
    }

    /** Returns the current epoch time in milliseconds. Extracted for test overriding. */
    protected long now() {
        return Instant.now().toEpochMilli();
    }

    @Override
    public String toString() {
        return String.format(
                "LeakyBucket{capacity=%d, leakRate=%.2f/s, water=%.2f, accepted=%d, dropped=%d}",
                capacity, leakRatePerSecond, currentWater, totalAccepted, totalDropped);
    }
}
