package com.example.eviction.policies;

import com.example.eviction.Cache;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Random Eviction Cache.
 * Randomly selects and evicts an element when capacity is exceeded.
 * Constant-time O(1) random key selection using HashMap + ArrayList.
 */
public class RandomEvictionCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, V> map;
    private final List<K> keys;
    private final Map<K, Integer> keyIndexMap;
    private final ReentrantLock lock = new ReentrantLock();

    public RandomEvictionCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.keys = new ArrayList<>();
        this.keyIndexMap = new HashMap<>();
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
            if (map.containsKey(key)) {
                map.put(key, value);
                return;
            }

            if (map.size() >= capacity) {
                evictRandom();
            }

            map.put(key, value);
            keys.add(key);
            keyIndexMap.put(key, keys.size() - 1);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        lock.lock();
        try {
            if (!map.containsKey(key)) {
                return false;
            }
            map.remove(key);
            removeKeyAtIndex(keyIndexMap.get(key));
            keyIndexMap.remove(key);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
            keys.clear();
            keyIndexMap.clear();
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

    private void evictRandom() {
        int randomIndex = ThreadLocalRandom.current().nextInt(keys.size());
        K evictKey = keys.get(randomIndex);
        map.remove(evictKey);
        removeKeyAtIndex(randomIndex);
        keyIndexMap.remove(evictKey);
        System.out.printf("[RANDOM EVICT] Evicted key at index %d: %s%n", randomIndex, evictKey);
    }

    // O(1) removal from ArrayList by swapping with the last element
    private void removeKeyAtIndex(int index) {
        int lastIndex = keys.size() - 1;
        if (index != lastIndex) {
            K lastKey = keys.get(lastIndex);
            keys.set(index, lastKey);
            keyIndexMap.put(lastKey, index);
        }
        keys.remove(lastIndex);
    }
}
