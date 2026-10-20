# Leaky Bucket Rate Limiter

A Java implementation of the **Leaky Bucket** algorithm — a classic rate-limiting and traffic-shaping technique used in networking and distributed systems.

---

## What is the Leaky Bucket Algorithm?

The Leaky Bucket algorithm controls the rate at which requests are processed, smoothing out bursty traffic into a steady output stream.

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

### How it works

| Step | Description |
|------|-------------|
| 1 | An incoming request is added to the bucket (queue). |
| 2 | If the bucket is **full**, the request is **dropped**. |
| 3 | The bucket continuously **leaks** (processes) requests at a fixed rate R. |
| 4 | The output is always at the constant rate, regardless of burst size. |

---

## Key Properties

| Property | Behaviour |
|----------|-----------|
| **Burst absorption** | Up to `capacity` requests can queue; beyond that they are dropped. |
| **Smooth output** | Traffic exits at a constant `leakRate` req/s regardless of how bursty arrivals are. |
| **Simplicity** | O(1) per request, no per-request timestamps needed (only time of last leak). |

---

## Difference from Token Bucket

| Leaky Bucket | Token Bucket |
|--------------|--------------|
| Fixed output rate (smooth) | Allows bursting up to token limit |
| Drops excess requests immediately | Excess requests wait or are dropped only when tokens run out |
| Best for traffic shaping | Best for rate limiting with burst tolerance |

---

## Project Structure

```
Leaky-Bucket/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/example/leakybucket/
    │   ├── LeakyBucket.java   ← core algorithm
    │   └── Main.java          ← demo / scenarios
    └── test/java/com/example/leakybucket/
        └── LeakyBucketTest.java
```

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Build

```bash
cd Leaky-Bucket
mvn clean compile
```

### Run Demo

```bash
mvn exec:java
```

**Sample output:**

```
=== Leaky Bucket Rate Limiter Demo ===

--- Scenario A: Burst of 15 requests (capacity=10, leak=5/s) ---
  Request  1 → ACCEPTED  (water=1.0)
  Request  2 → ACCEPTED  (water=2.0)
  ...
  Request 10 → ACCEPTED  (water=10.0)
  Request 11 → DROPPED   (water=10.0)
  Request 12 → DROPPED   (water=10.0)
  ...
Total accepted: 10 | Total dropped: 5

--- Scenario B: Steady rate ~3.3 req/s (capacity=10, leak=5/s) ---
  Request  1 → ACCEPTED  (water=1.0)
  Request  2 → ACCEPTED  (water=...)
  ...
Total accepted: 10 | Total dropped: 0
```

### Run Tests

```bash
mvn test
```

---

## Algorithm Complexity

| Dimension | Value |
|-----------|-------|
| Time per request | O(1) |
| Space | O(1) |

---

## Use Cases

- **API rate limiting** — prevent a single client from overloading a service.
- **Network traffic shaping** — enforce a constant egress rate on a link.
- **Message queue ingestion** — smooth bursty producers before writing to a database.
- **Microservice protection** — shed load gracefully during traffic spikes.
