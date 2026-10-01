# Cache Eviction Policies in System Design (Core Java)

This repository provides clean, production-grade, thread-safe Core Java implementations of the most widely used **Cache Eviction Policies** in distributed and in-memory systems.

---

## Implemented Policies

| Policy | Data Structure | Time Complexity (Get / Put / Evict) | Eviction Rule | Primary Use Case |
| :--- | :--- | :--- | :--- | :--- |
| **LRU** (Least Recently Used) | HashMap + Doubly Linked List | **O(1) / O(1) / O(1)** | Discards items not accessed for the longest period. | General web workloads, CDN edges, Redis default. |
| **LFU** (Least Frequently Used) | HashMap + Frequency Buckets (`LinkedHashSet`) | **O(1) / O(1) / O(1)** | Discards items with lowest access frequency (breaks ties via LRU). | Power-law / Pareto distributions (80/20 rule), hot product catalogs. |
| **FIFO** (First-In First-Out) | `LinkedHashMap` | **O(1) / O(1) / O(1)** | Discards oldest inserted items regardless of access patterns. | Time-series, telemetry, sequential streaming pipelines. |
| **TTL** (Time-To-Live) | `ConcurrentHashMap` + Scheduled Sweep | **O(1) / O(1) / O(1)** | Proactively & passively discards entries after expiration time. | Auth tokens, rate-limiters, session caches. |
| **Random Eviction** | HashMap + Dynamic Array (Index Swap) | **O(1) / O(1) / O(1)** | Randomly selects a victim to discard. | Low-overhead caches with uniform access patterns. |

---

## Project Structure

```
Caching-Evication/
+-- pom.xml
+-- README.md
+-- src/
    +-- main/java/com/example/eviction/
    ¦   +-- Cache.java                     # Common generic Cache interface
    ¦   +-- Main.java                      # Interactive demonstration runner
    ¦   +-- policies/
    ¦       +-- LruCache.java              # O(1) LRU Cache (Doubly Linked List + Map)
    ¦       +-- LfuCache.java              # O(1) LFU Cache (Frequency Buckets + Map)
    ¦       +-- FifoCache.java             # FIFO Cache (Insertion-Ordered LinkedHashMap)
    ¦       +-- TtlCache.java              # TTL Cache (Passive get() check + Active sweep)
    ¦       +-- RandomEvictionCache.java   # O(1) Random Eviction (Swap-to-tail array)
    +-- test/java/com/example/eviction/
        +-- CacheEvictionTest.java         # Full JUnit 5 test suite
```

---

## Build & Test

```powershell
# Run Unit Tests
mvn test

# Run Demo Application
mvn compile exec:java -Dexec.mainClass="com.example.eviction.Main"
```
