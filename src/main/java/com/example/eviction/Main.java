package com.example.eviction;

import com.example.eviction.policies.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("==================================================");
        System.out.println("  1. LRU (Least Recently Used) Cache Demo");
        System.out.println("==================================================");
        Cache<String, String> lru = new LruCache<>(3);
        lru.put("A", "Alpha");
        lru.put("B", "Beta");
        lru.put("C", "Gamma");
        lru.get("A"); // A accessed, LRU order becomes: B, C, A
        System.out.println("Putting D (should evict B):");
        lru.put("D", "Delta");
        System.out.println("Contains A? " + lru.get("A").isPresent());
        System.out.println("Contains B? " + lru.get("B").isPresent()); // false

        System.out.println("\n==================================================");
        System.out.println("  2. LFU (Least Frequently Used) Cache Demo");
        System.out.println("==================================================");
        Cache<String, String> lfu = new LfuCache<>(3);
        lfu.put("A", "Alpha");
        lfu.put("B", "Beta");
        lfu.put("C", "Gamma");
        lfu.get("A"); // Freq(A)=2
        lfu.get("A"); // Freq(A)=3
        lfu.get("B"); // Freq(B)=2
        // C has Freq=1
        System.out.println("Putting D (should evict C due to lowest frequency):");
        lfu.put("D", "Delta");
        System.out.println("Contains C? " + lfu.get("C").isPresent()); // false
        System.out.println("Contains A? " + lfu.get("A").isPresent()); // true

        System.out.println("\n==================================================");
        System.out.println("  3. FIFO (First-In First-Out) Cache Demo");
        System.out.println("==================================================");
        Cache<String, String> fifo = new FifoCache<>(3);
        fifo.put("A", "Alpha");
        fifo.put("B", "Beta");
        fifo.put("C", "Gamma");
        fifo.get("A"); // FIFO ignores access
        System.out.println("Putting D (should evict A as oldest inserted):");
        fifo.put("D", "Delta");
        System.out.println("Contains A? " + fifo.get("A").isPresent()); // false

        System.out.println("\n==================================================");
        System.out.println("  4. TTL (Time-To-Live) Cache Demo");
        System.out.println("==================================================");
        try (TtlCache<String, String> ttl = new TtlCache<>(3, 1500)) { // 1.5s TTL
            ttl.put("A", "Alpha");
            ttl.put("B", "Beta");
            System.out.println("Immediate read A: " + ttl.get("A").orElse("Expired"));
            System.out.println("Waiting 2 seconds for TTL expiration...");
            Thread.sleep(2000);
            System.out.println("Read A after 2s: " + ttl.get("A").orElse("Expired")); // Expired
        }

        System.out.println("\n==================================================");
        System.out.println("  5. Random Eviction Cache Demo");
        System.out.println("==================================================");
        Cache<String, String> random = new RandomEvictionCache<>(3);
        random.put("A", "Alpha");
        random.put("B", "Beta");
        random.put("C", "Gamma");
        System.out.println("Putting D (should evict a randomly selected key):");
        random.put("D", "Delta");
        System.out.printf("Cache size after eviction: %d (capacity: %d)%n", random.size(), random.capacity());

        System.out.println("\nAll Cache Eviction demonstrations finished.");
    }
}
