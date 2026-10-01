package com.example.eviction;

import com.example.eviction.policies.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CacheEvictionTest {

    @Test
    void testLruEviction() {
        Cache<Integer, String> cache = new LruCache<>(2);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.get(1); // 1 is now most recently used; 2 is LRU
        cache.put(3, "three"); // Should evict 2

        assertTrue(cache.get(1).isPresent());
        assertFalse(cache.get(2).isPresent());
        assertTrue(cache.get(3).isPresent());
    }

    @Test
    void testLfuEviction() {
        Cache<Integer, String> cache = new LfuCache<>(2);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.get(1); // Freq(1) = 2
        cache.put(3, "three"); // Freq(2) = 1, evicts 2

        assertTrue(cache.get(1).isPresent());
        assertFalse(cache.get(2).isPresent());
        assertTrue(cache.get(3).isPresent());
    }

    @Test
    void testFifoEviction() {
        Cache<Integer, String> cache = new FifoCache<>(2);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.get(1); // Access doesn't affect insertion order
        cache.put(3, "three"); // Evicts oldest (1)

        assertFalse(cache.get(1).isPresent());
        assertTrue(cache.get(2).isPresent());
        assertTrue(cache.get(3).isPresent());
    }

    @Test
    void testTtlEviction() throws InterruptedException {
        try (TtlCache<Integer, String> cache = new TtlCache<>(2, 200)) {
            cache.put(1, "one");
            assertTrue(cache.get(1).isPresent());
            Thread.sleep(300);
            assertFalse(cache.get(1).isPresent());
        }
    }

    @Test
    void testRandomEviction() {
        Cache<Integer, String> cache = new RandomEvictionCache<>(2);
        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");
        assertEquals(2, cache.size());
    }
}
