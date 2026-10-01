package com.example.eviction.policies;

import com.example.eviction.Cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TTL (Time-To-Live) Cache with Passive & Active Eviction.
 * - Passive: Checked and discarded during get() if expired.
 * - Active: Background cleaner periodically sweeps expired entries.
 * - Capacity Eviction: Evicts nearest-to-expire entry if capacity is exceeded before TTL expires.
 */
public class TtlCache<K, V> implements Cache<K, V>, AutoCloseable {
    private static class Entry<V> {
        final V value;
        final long expiryTimeMillis;

        Entry(V value, long ttlMillis) {
            this.value = value;
            this.expiryTimeMillis = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTimeMillis;
        }
    }

    private final int capacity;
    private final long defaultTtlMillis;
    private final Map<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ReentrantLock lock = new ReentrantLock();

    public TtlCache(int capacity, long defaultTtlMillis) {
        if (capacity <= 0 || defaultTtlMillis <= 0) {
            throw new IllegalArgumentException("Capacity and TTL must be greater than 0");
        }
        this.capacity = capacity;
        this.defaultTtlMillis = defaultTtlMillis;

        // Active background cleanup every 1 second
        cleanupScheduler.scheduleAtFixedRate(this::activeCleanup, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public Optional<V> get(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        // Passive eviction check
        if (entry.isExpired()) {
            map.remove(key);
            System.out.printf("[TTL PASSIVE EVICT] Key expired: %s%n", key);
            return Optional.empty();
        }

        return Optional.of(entry.value);
    }

    @Override
    public void put(K key, V value) {
        put(key, value, defaultTtlMillis);
    }

    public void put(K key, V value, long ttlMillis) {
        lock.lock();
        try {
            if (map.size() >= capacity && !map.containsKey(key)) {
                evictNearestExpiry();
            }
            map.put(key, new Entry<>(value, ttlMillis));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        return map.remove(key) != null;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public int size() {
        return (int) map.entrySet().stream().filter(e -> !e.getValue().isExpired()).count();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    private void activeCleanup() {
        try {
            map.entrySet().removeIf(entry -> {
                boolean expired = entry.getValue().isExpired();
                if (expired) {
                    System.out.printf("[TTL ACTIVE SWEEP] Cleaned up expired key: %s%n", entry.getKey());
                }
                return expired;
            });
        } catch (Exception e) {
            System.err.println("Active sweep error: " + e.getMessage());
        }
    }

    private void evictNearestExpiry() {
        K earliestKey = null;
        long minExpiry = Long.MAX_VALUE;

        for (Map.Entry<K, Entry<V>> entry : map.entrySet()) {
            if (entry.getValue().expiryTimeMillis < minExpiry) {
                minExpiry = entry.getValue().expiryTimeMillis;
                earliestKey = entry.getKey();
            }
        }

        if (earliestKey != null) {
            map.remove(earliestKey);
            System.out.printf("[TTL CAPACITY EVICT] Evicted key with nearest expiration: %s%n", earliestKey);
        }
    }

    @Override
    public void close() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
