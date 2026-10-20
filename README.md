# System Design — Java Reference Implementations

A growing collection of **production-grade Java implementations** of classic system design concepts, data structures, and algorithms. Each module is a self-contained Maven project with clean code, JUnit 5 tests, and a detailed README covering the theory, tradeoffs, and usage guidance.

> **Language:** Java 17 · **Build:** Maven 3.8+ · **Tests:** JUnit 5

---

## Modules

| # | Module | Category | What it covers |
|---|--------|----------|----------------|
| 1 | [Bloom-Filter](#1-bloom-filter) | Probabilistic Data Structures | Space-efficient set membership testing |
| 2 | [Caching-Eviction](#2-caching-eviction) | Caching | LRU, LFU, FIFO, TTL, Random eviction policies |
| 3 | [Caching-Strategy](#3-caching-strategy) | Caching | Cache-Aside, Write-Through, Write-Behind, Write-Around, Refresh-Ahead |
| 4 | [Consistent-Hashing](#4-consistent-hashing) | Distributed Systems | Hash ring with virtual nodes, Rendezvous, Jump Hash |
| 5 | [Database-Locking](#5-database-locking) | Databases | Optimistic, Pessimistic, 2PL, Intent Locks, Deadlock prevention |
| 6 | [Leaky-Bucket](#6-leaky-bucket) | Rate Limiting | Fixed-rate traffic shaping with a leaky bucket |
| 7 | [Lossy-Count](#7-lossy-count) | Streaming Algorithms | Frequent items / heavy hitters in unbounded streams |
| 8 | [Ray-Casting](#8-ray-casting) | Geospatial | Point-in-polygon, geofencing, S2, H3, PostGIS, GeoTools |

---

## 1. Bloom Filter

**`Bloom-Filter/`** · [README](Bloom-Filter/README.md)

A **Bloom Filter** is a space-efficient probabilistic data structure that tests set membership. It guarantees **no false negatives** — if it says "not present", the element is definitely absent — but may produce false positives.

### Key concepts
- Fixed-size bit array + `k` independent hash functions (SHA-256 based)
- Insertion sets `k` bits; lookup checks all `k` bits
- False positive rate: `P = (1 − e^(−k·n/m))^k`
- Optimal hash count: `k = (m/n) · ln(2)`

### When to use
- Database negative-lookup guards (Cassandra, HBase, RocksDB)
- Cache penetration prevention (pre-filter before Redis)
- Duplicate URL detection in web crawlers
- Banned username / email pre-check before hitting the DB
- Millions of elements, tunable false-positive rate (0.1–1%), memory-constrained

### When NOT to use
- Exact membership required (use `HashSet`)
- Deletions needed (use Counting Bloom Filter)
- Dataset fits comfortably in memory
- False positives have severe consequences (security, financial)

### Build & run
```bash
cd Bloom-Filter
mkdir -p out
javac -d out src/main/java/com/bloomfilter/HashUtils.java \
             src/main/java/com/bloomfilter/BloomFilter.java \
             src/main/java/com/bloomfilter/BloomFilterExample.java
java -cp out com.bloomfilter.BloomFilterExample
```

---

## 2. Caching-Eviction

**`Caching-Evication/`** · [README](Caching-Evication/README.md)

Five **O(1) cache eviction policies** implemented in Core Java behind a common generic `Cache<K,V>` interface. All implementations are thread-safe.

### Policies implemented

| Policy | Data Structure | Best Used For |
|--------|---------------|---------------|
| **LRU** | `HashMap` + Doubly Linked List | General web & API traffic (temporal locality) |
| **LFU** | `HashMap` + Frequency Buckets (`LinkedHashSet`) | Long-term hot items, 80/20 Pareto workloads |
| **FIFO** | `LinkedHashMap` (insertion order) | Sequential pipelines, streaming ingest |
| **TTL** | `ConcurrentHashMap` + `ScheduledExecutorService` | Ephemeral data — auth tokens, sessions, rate limiters |
| **Random** | `HashMap` + Swap-to-Tail ArrayList | Uniform access distributions, minimal overhead |

### Selection guide
```
Does the data have a strict expiry lifespan?  → TTL Cache
Sequential / stream processing?               → FIFO Cache
Long-term historical popularity?              → LFU Cache
General-purpose / recent access bias?         → LRU Cache  ← default
Uniform access / minimal memory budget?       → Random Eviction
```

### Build & run
```bash
cd Caching-Evication
mvn test
mvn compile exec:java -Dexec.mainClass="com.example.eviction.Main"
```

---

## 3. Caching-Strategy

**`Caching-Strategy/`** · [README](Caching-Strategy/README.md)

A comprehensive guide and Core Java implementation of **read/write caching strategies**, distributed failure modes and mitigations, and advanced patterns — with Redis integration.

### Strategies implemented

| Strategy | Read Latency | Write Latency | Consistency | Best Fit |
|----------|-------------|---------------|-------------|----------|
| **Cache-Aside** | Low (hit) / High (miss) | Fast | Eventual | General-purpose web apps |
| **Write-Through** | Ultra-Low | Higher | High | Financial / eCommerce catalogs |
| **Write-Behind** | Ultra-Low | Ultra-Low | Eventual (lag risk) | High-volume ingestion, telemetry |
| **Write-Around** | High on 1st read | Fast | High in DB | Rarely-read records (audit trails) |
| **Refresh-Ahead** | Near-zero misses | Normal | High | Ultra-hot keys (trending feeds, leaderboards) |

### Failure modes covered
- **Cache Stampede (Thundering Herd)** — Mutex / Singleflight, XFetch probabilistic early expiration
- **Cache Avalanche** — Random TTL jitter, staggered warm-up
- **Cache Penetration** — Null sentinel caching, Bloom Filter guard
- **Data Inconsistency** — CDC (Debezium), Transactional Outbox pattern

### Advanced patterns
- **Multi-Level Cache (L1 + L2)** — local heap cache + Redis
- **Cache Warmer** — pre-populate hot keys before live traffic
- **Segmented Cache** — namespace prefixing for independent TTL and flush per entity type

### Build & run
```bash
docker run -d -p 6379:6379 --name redis-caching-demo redis:alpine
cd Caching-Strategy
mvn compile exec:java -Dexec.mainClass="com.example.caching.Main"
```

---

## 4. Consistent-Hashing

**`Consistant-Hashing/`** · [README](Consistant-Hashing/README.md)

Four **key-to-node mapping strategies** benchmarked side-by-side on 100,000 keys using a shared Murmur3 hash (via Guava).

### Strategies implemented

| Strategy | Keys moved adding 1 node to 4 | Notes |
|----------|-------------------------------|-------|
| **Modulo** | ~80% | Breaks on any topology change |
| **Consistent Hash Ring** (1 vnode) | ~3% (lucky; unpredictable) | Uneven without vnodes |
| **Consistent Hash Ring** (150 vnodes) | ~18% ≈ ideal 20% | Production default |
| **Rendezvous (HRW)** | ~20% (exact) | O(N) lookup; good for small clusters |
| **Jump Hash** (Guava) | ~20% | O(1), cannot remove arbitrary nodes |

### Key concepts
- **Virtual nodes** — each physical node placed N times on the ring for even load
- **Weighted nodes** — more vnodes → proportionally more keys
- **Replica placement** — walk clockwise, collect next R distinct physical nodes
- **Cascading load** — 1 vnode: failed node's load lands on 1 neighbour; 150 vnodes: spread across all 9 survivors

### When to use
Distributed caches, partitioned databases (Cassandra, Dynamo-style), CDN routing, sticky load balancing, sharded message processing.

### Build & run
```bash
cd Consistant-Hashing
mvn test          # 17 unit tests
mvn compile exec:java
```

---

## 5. Database-Locking

**`Database-Locking/`** · [README](Database-Locking/README.md)

Six **locking mechanisms** implemented in Core Java, covering the full spectrum from optimistic to strict two-phase locking, plus deadlock simulation and prevention.

### Locking mechanisms

| Lock Type | Mechanism | Use Case |
|-----------|-----------|----------|
| **Optimistic Locking** | Version number check on write | Low-contention, read-heavy workloads |
| **Pessimistic Locking** | `ReentrantLock` acquired before read | High-contention, write-heavy workloads |
| **Read/Write Lock** | `ReadWriteLock` — shared reads, exclusive writes | Read-heavy with occasional writes |
| **Two-Phase Locking (2PL)** | Grow phase (acquire) then shrink phase (release) | Serializable isolation |
| **Intent Locks (IS/IX/S/X)** | Hierarchical locking with compatibility matrix | Row + table level locking |
| **Deadlock** | Simulation + prevention via lock ordering & timeout | Understanding and avoiding deadlocks |

### Isolation levels & anomalies covered
- Dirty Read, Non-Repeatable Read, Phantom Read, Lost Update
- Transaction propagation strategies

### Build & run
```bash
cd Database-Locking
mvn test
mvn compile exec:java
```

---

## 6. Leaky-Bucket

**`Leaky-Bucket/`** · [README](Leaky-Bucket/README.md)

The **Leaky Bucket** algorithm for rate limiting and traffic shaping — incoming requests are queued in a fixed-capacity bucket; the bucket leaks at a constant rate, producing a smooth output stream regardless of bursty arrivals.

```
Incoming Requests (bursty)
          │
          ▼
   ┌──────────────┐
   │    BUCKET    │  capacity = N  →  drop if full
   └──────┬───────┘
          │  leak rate = R req/s  (constant)
          ▼
   Outgoing Stream (smooth)
```

### Key properties
- **O(1)** time and space per request
- Thread-safe (`synchronized`)
- Controllable-clock subclass for deterministic unit tests (no `Thread.sleep`)
- Tracks total accepted / dropped counts

### Leaky Bucket vs Token Bucket

| | Leaky Bucket | Token Bucket |
|--|-------------|--------------|
| Output | Fixed rate (smooth) | Burst up to token limit |
| Excess handling | Dropped immediately | Wait or drop when tokens exhausted |
| Best for | Traffic shaping | Rate limiting with burst tolerance |

### When to use
- API rate limiting — prevent single clients from overloading a service
- Network traffic shaping — enforce constant egress rate
- Message queue ingestion — smooth bursty producers
- Microservice protection — shed load during spikes

### Build & run
```bash
cd Leaky-Bucket
mvn test          # 14 unit tests
mvn exec:java     # two demo scenarios
```

---

## 7. Lossy-Count

**`Lossy-Count/`** · [README](Lossy-Count/README.md)

The **Lossy Counting** algorithm (Manku & Motwani, 2002) finds **frequent items (heavy hitters)** in an unbounded data stream using `O((1/ε) log(εN))` memory — independent of the number of distinct items.

### Algorithm

For each arriving item `e` with current bucket `b`:
1. If `e` is in the table → increment `f`
2. Otherwise insert `(e, 1, b − 1)`
3. At every bucket boundary → prune entries where `f + Δ ≤ b`

Query: return entries with `f ≥ (s − ε) × N`

### Guarantees

| Guarantee | Meaning |
|-----------|---------|
| `f_true − ε·N ≤ f_est ≤ f_true` | Never overcounts; undercounts by at most `ε·N` |
| No false negatives | Every item with `f_true ≥ s·N` is always reported |
| Bounded false positives | No item with `f_true < (s−ε)·N` is reported |

### Real results (1M items, Zipf stream, s=1%, ε=0.1%)

```
Exact counter:   64,723 counters
Lossy counter:   138 counters (567 peak)  ← 114x fewer
False negatives: 0   (guaranteed)
False positives: 1   (within the allowed margin)
```

### When to use
Network monitoring (DDoS detection), trending queries/hashtags, log analytics, ad click analytics, hot-key detection for caches.

### Build & run
```bash
cd Lossy-Count
mvn test          # 18 unit tests
mvn compile exec:java
```

---

## 8. Ray-Casting

**`Ray-Casting/`** · [README](Ray-Casting/README.md)

The **ray casting algorithm** for point-in-polygon tests, extended into a full geofencing system covering cell-based indexes (Google S2, Uber H3), PostGIS, and GeoTools.

### How it works
1. Cast a ray from the point horizontally to +∞
2. Count polygon edge crossings
3. **Odd** count → inside; **Even** count → outside

### Implementations compared

| Approach | Query Cost | Exactness | Best For |
|----------|-----------|-----------|----------|
| **Hand-written ray casting** | O(n) edges | Exact (planar) | Few zones, no dependencies |
| **JTS PreparedGeometry** | O(log n) | Exact | High-QPS, complex polygons |
| **AWT Path2D** (JDK) | O(n) | Exact | Zero dependencies, simple shapes |
| **Google S2** | O(levels) hash lookups | Exact (spherical) | Many zones, global coordinates |
| **Uber H3** | O(1) hash lookup | Approximate near borders | Analytics, surge pricing, heatmaps |
| **PostGIS + GiST** | O(log n) + network | Exact | Zones in DB, distance queries, shared |
| **GeoTools STRtree** | O(log n) | Exact | In-memory, CRS reprojection, multi-format |

### Benchmark (10,000 zones, 2,000 random points)
```
Ray casting + bbox loop :   0.258 ms/query
GeoTools STRtree        :   0.012 ms/query
PostGIS with GiST       :   0.686 ms/query  (includes JDBC round-trip)
```

### When to use
Geofencing (ride-hailing zones, delivery boundaries), map region lookup, click detection on irregular shapes, GIS point queries.

### Build & run
```bash
cd Ray-Casting
docker compose up -d          # optional: needed only for PostGIS tests
mvn test
mvn compile exec:java
docker compose down
```

---

## Getting Started

### Prerequisites
- **JDK 17+**
- **Maven 3.8+**
- **Docker** (optional — only required for `Caching-Strategy` Redis demo and `Ray-Casting` PostGIS tests)

### Clone
```bash
git clone https://github.com/Jatinghataliya/SYSTEM-DESIGN.git
cd SYSTEM-DESIGN
```

### Run all tests for a module
```bash
cd <module-folder>
mvn clean test
```

### Run the demo for a module
```bash
cd <module-folder>
mvn compile exec:java
```

---

## Author

**Jatin Ghataliya** · [prajapati.jatin94@gmail.com](mailto:prajapati.jatin94@gmail.com)
