package com.example.lossycount;

import java.util.HashMap;
import java.util.Map;

/** Baseline: one counter per distinct item. Exact, but memory grows with distinct items. */
public class ExactCounter<T> {

    private final Map<T, Long> counts = new HashMap<>();
    private long n;

    public void add(T item) {
        n++;
        counts.merge(item, 1L, Long::sum);
    }

    public long count(T item) {
        return counts.getOrDefault(item, 0L);
    }

    public long itemsSeen() {
        return n;
    }

    /** Number of distinct items, which is also the number of counters held. */
    public int size() {
        return counts.size();
    }

    public Map<T, Long> counts() {
        return counts;
    }
}
