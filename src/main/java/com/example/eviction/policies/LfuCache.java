package com.example.eviction.policies;

import com.example.eviction.Cache;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LFU (Least Frequently Used) Cache.
 * Evicts keys with the lowest access frequency. On frequency tie, breaks tie via LRU.
 * Implemented using Map + Frequency Buckets (LinkedHashSet) for O(1) operations.
 */
public class LfuCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private int minFrequency;
    private final Map<K, V> values;
    private final Map<K, Integer> counts;
    private final Map<Integer, LinkedHashSet<K>> frequencyMap;
    private final ReentrantLock lock = new ReentrantLock();

    public LfuCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.minFrequency = 0;
        this.values = new HashMap<>();
        this.counts = new HashMap<>();
        this.frequencyMap = new HashMap<>();
    }

    @Override
    public Optional<V> get(K key) {
        lock.lock();
        try {
            if (!values.containsKey(key)) {
                return Optional.empty();
            }
            incrementFrequency(key);
            return Optional.of(values.get(key));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        lock.lock();
        try {
            if (values.containsKey(key)) {
                values.put(key, value);
                incrementFrequency(key);
                return;
            }

            if (values.size() >= capacity) {
                evict();
            }

            values.put(key, value);
            counts.put(key, 1);
            minFrequency = 1;
            frequencyMap.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        lock.lock();
        try {
            if (!values.containsKey(key)) {
                return false;
            }
            int freq = counts.remove(key);
            values.remove(key);
            LinkedHashSet<K> set = frequencyMap.get(freq);
            if (set != null) {
                set.remove(key);
                if (set.isEmpty() && freq == minFrequency) {
                    minFrequency++;
                }
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            values.clear();
            counts.clear();
            frequencyMap.clear();
            minFrequency = 0;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return values.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    private void incrementFrequency(K key) {
        int count = counts.get(key);
        counts.put(key, count + 1);

        LinkedHashSet<K> currentSet = frequencyMap.get(count);
        currentSet.remove(key);

        if (count == minFrequency && currentSet.isEmpty()) {
            minFrequency++;
        }

        frequencyMap.computeIfAbsent(count + 1, k -> new LinkedHashSet<>()).add(key);
    }

    private void evict() {
        LinkedHashSet<K> minFreqSet = frequencyMap.get(minFrequency);
        if (minFreqSet != null && !minFreqSet.isEmpty()) {
            K evictKey = minFreqSet.iterator().next();
            minFreqSet.remove(evictKey);
            counts.remove(evictKey);
            values.remove(evictKey);
            System.out.printf("[LFU EVICT] Evicted key: %s (Frequency: %d)%n", evictKey, minFrequency);
        }
    }
}
