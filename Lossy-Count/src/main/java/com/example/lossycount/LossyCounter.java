package com.example.lossycount;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Lossy Counting (Manku and Motwani, 2002).
 *
 * <p>Finds frequent items in a stream using bounded memory. The stream is split into buckets of
 * {@code w = ceil(1/epsilon)} items. Each tracked item keeps {@code f} (count since insertion) and
 * {@code delta} (maximum possible undercount). At every bucket boundary, entries with
 * {@code f + delta <= currentBucket} are dropped.
 *
 * <p>Guarantees, for any item with true count {@code t} after {@code N} items:
 * <ul>
 *   <li>{@code t - epsilon * N <= estimate <= t} (never overcounts)</li>
 *   <li>every item with {@code t >= s * N} is reported by {@link #frequentItems(double)}</li>
 *   <li>no item with {@code t < (s - epsilon) * N} is reported</li>
 * </ul>
 *
 * <p>Not thread-safe.
 */
public class LossyCounter<T> {

    /** One tracked item. */
    public static final class Entry<T> {
        private final T item;
        private long count;       // f
        private final long delta; // max undercount

        private Entry(T item, long count, long delta) {
            this.item = item;
            this.count = count;
            this.delta = delta;
        }

        public T item()    { return item; }
        public long count() { return count; }
        public long delta() { return delta; }

        @Override
        public String toString() {
            return item + "(f=" + count + ", delta=" + delta + ")";
        }
    }

    private final double epsilon;
    private final long bucketWidth;
    private final Map<T, Entry<T>> table = new HashMap<>();

    private long n;        // items seen
    private int peakSize;  // largest table size observed

    public LossyCounter(double epsilon) {
        if (!(epsilon > 0 && epsilon < 1)) {
            throw new IllegalArgumentException("epsilon must be in (0, 1): " + epsilon);
        }
        this.epsilon = epsilon;
        this.bucketWidth = (long) Math.ceil(1.0 / epsilon);
    }

    /** Process one item from the stream. */
    public void add(T item) {
        n++;
        long currentBucket = currentBucket();

        Entry<T> e = table.get(item);
        if (e != null) {
            e.count++;
        } else {
            table.put(item, new Entry<>(item, 1, currentBucket - 1));
        }
        peakSize = Math.max(peakSize, table.size());

        if (n % bucketWidth == 0) {
            prune(currentBucket);
        }
    }

    private void prune(long currentBucket) {
        Iterator<Entry<T>> it = table.values().iterator();
        while (it.hasNext()) {
            Entry<T> e = it.next();
            if (e.count + e.delta <= currentBucket) {
                it.remove();
            }
        }
    }

    /**
     * Items whose estimated count is at least {@code (support - epsilon) * N}, most frequent first.
     * Contains every item with true frequency {@code >= support * N}.
     */
    public List<Entry<T>> frequentItems(double support) {
        if (!(support > epsilon && support <= 1)) {
            throw new IllegalArgumentException("support must be in (epsilon, 1]: " + support);
        }
        double threshold = (support - epsilon) * n;
        List<Entry<T>> result = new ArrayList<>();
        for (Entry<T> e : table.values()) {
            if (e.count >= threshold) {
                result.add(e);
            }
        }
        result.sort(Comparator.comparingLong((Entry<T> e) -> e.count).reversed());
        return result;
    }

    /** Estimated count of an item: a lower bound on its true count. 0 if it is not tracked. */
    public long estimate(T item) {
        Entry<T> e = table.get(item);
        return e == null ? 0 : e.count;
    }

    /** Largest amount by which any estimate can be too low: {@code epsilon * N}. */
    public double maxError() {
        return epsilon * n;
    }

    /** Number of items seen so far. */
    public long itemsSeen() {
        return n;
    }

    /** Number of entries currently tracked. */
    public int size() {
        return table.size();
    }

    /** Largest number of entries tracked at any point so far. */
    public int peakSize() {
        return peakSize;
    }

    public long bucketWidth() {
        return bucketWidth;
    }

    public double epsilon() {
        return epsilon;
    }

    /** Id of the bucket the most recent item belongs to: {@code ceil(N / w)}. */
    public long currentBucket() {
        return (n + bucketWidth - 1) / bucketWidth;
    }

    /** Snapshot of the table, for inspection and tracing. */
    public List<Entry<T>> entries() {
        return new ArrayList<>(table.values());
    }
}
