package com.example.reminder.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory distributed cache simulation (Redis SETNX with TTL) for notification deduplication.
 */
public class IdempotencyStore {

    private record Entry(long expiresAtMs) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong duplicateCount = new AtomicLong(0);

    /**
     * Attempts to acquire an idempotency lock for the given key.
     *
     * @param key        unique idempotency key
     * @param ttlSeconds lock time-to-live
     * @return true if acquired (first time seeing this key); false if already processed (duplicate)
     */
    public boolean tryAcquire(String key, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long expireTime = now + (ttlSeconds * 1000);

        Entry existing = store.putIfAbsent(key, new Entry(expireTime));
        if (existing == null) {
            return true;
        }

        if (now > existing.expiresAtMs()) {
            store.put(key, new Entry(expireTime));
            return true;
        }

        duplicateCount.incrementAndGet();
        return false;
    }

    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    public int size() {
        return store.size();
    }
}
