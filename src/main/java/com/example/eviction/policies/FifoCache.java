package com.example.eviction.policies;

import com.example.eviction.Cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * FIFO (First-In First-Out) Cache.
 * Evicts the oldest inserted keys first, regardless of access patterns.
 */
public class FifoCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, V> map;
    private final ReentrantLock lock = new ReentrantLock();

    public FifoCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        // LinkedHashMap with accessOrder=false maintains insertion order (FIFO)
        this.map = new LinkedHashMap<K, V>(capacity, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                boolean shouldEvict = size() > FifoCache.this.capacity;
                if (shouldEvict) {
                    System.out.printf("[FIFO EVICT] Evicted oldest key: %s%n", eldest.getKey());
                }
                return shouldEvict;
            }
        };
    }

    @Override
    public Optional<V> get(K key) {
        lock.lock();
        try {
            return Optional.ofNullable(map.get(key));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        lock.lock();
        try {
            map.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        lock.lock();
        try {
            return map.remove(key) != null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
