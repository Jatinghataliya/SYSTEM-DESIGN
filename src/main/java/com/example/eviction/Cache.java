package com.example.eviction;

import java.util.Optional;

public interface Cache<K, V> {
    Optional<V> get(K key);
    void put(K key, V value);
    boolean remove(K key);
    void clear();
    int size();
    int capacity();
}
