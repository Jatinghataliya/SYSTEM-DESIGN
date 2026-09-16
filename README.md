# 🌸 Bloom Filter — Java Implementation

A clean, well-documented implementation of the **Bloom Filter** probabilistic data structure in Java, with three live examples covering banned username checking, optimal configuration analysis, and large-scale membership testing.

---

## 📖 What is a Bloom Filter?

A **Bloom Filter** is a **space-efficient probabilistic data structure** used to test whether an element is a member of a set. It was invented by **Burton Howard Bloom in 1970**.

Instead of storing actual elements, a Bloom Filter uses a **fixed-size bit array** and **multiple hash functions** to record the presence of elements. This design makes it extremely memory-efficient, but introduces a deliberate trade-off:

| Guarantee | Detail |
|---|---|
| ✅ **No False Negatives** | If the filter says "NOT present", the element is **definitely absent** |
| ⚠️ **Possible False Positives** | If the filter says "present", the element **might or might not** actually be there |

> In short: a Bloom Filter answers *"Has this element **definitely never** been seen, or **possibly** been seen?"*

---

## ⚙️ How It Works

### Insertion
1. Pass the element through **k** hash functions.
2. Each hash function returns an index into the bit array.
3. Set all **k** bit positions to `1`.

### Lookup
1. Pass the element through the same **k** hash functions.
2. Check all **k** bit positions.
3. If **all bits = 1** → element **possibly** in set *(may be a false positive)*
4. If **any bit = 0** → element is **definitely NOT** in the set

### False Positive Rate Formula

```
P = (1 - e^(-k*n/m))^k
```

Where:
- `m` = size of the bit array
- `n` = number of elements inserted
- `k` = number of hash functions

### Optimal number of hash functions

```
k = (m/n) * ln(2)
```

---

## 🎯 Purpose

The primary purpose of a Bloom Filter is to provide **ultra-fast, memory-efficient set membership testing** where:

- **Speed** matters more than perfect accuracy
- **Memory** is constrained
- **False positives** are acceptable and can be handled downstream
- **False negatives** are unacceptable

It acts as a **probabilistic guard** — a cheap first-pass filter that prevents expensive operations (disk reads, network calls, database queries) for elements that are definitely not in the set.

---

## ✅ Where You SHOULD Use a Bloom Filter

| Use Case | Why It Fits |
|---|---|
| **Database query optimization** | Avoid expensive disk lookups for keys that don't exist (used in Cassandra, HBase, RocksDB) |
| **Web browser safe-browsing** | Google Chrome used Bloom Filters to check malicious URLs before a full server lookup |
| **Spell checkers** | Quickly check if a word exists in a dictionary without loading the entire dictionary |
| **CDN / Cache optimization** | Avoid caching "one-hit-wonder" items that are only requested once |
| **Duplicate detection** | Detect duplicate URLs during web crawling (used by Google) |
| **Blockchain / Bitcoin** | Filter relevant transactions in SPV (Simplified Payment Verification) clients |
| **Email spam filtering** | First-pass check against known spam patterns |
| **Distributed systems** | Reduce cross-node network calls for membership checks |
| **Rate limiting** | Track seen tokens/IPs with minimal memory |
| **Username / email availability** | Quick pre-check before hitting the database |

### Key Signal: Use a Bloom Filter when...
- You have **millions or billions** of elements to track
- You want to avoid **expensive I/O** for negative lookups
- You can **tolerate a small, tunable false positive rate** (e.g., 0.1–1%)
- Memory is **scarce** compared to the dataset size

---

## ❌ Where You Should NOT Use a Bloom Filter

| Situation | Why It Doesn't Fit |
|---|---|
| **You need exact membership testing** | False positives make it unsuitable for security-critical exact matches |
| **You need to delete elements** | Standard Bloom Filters don't support deletion (use a Counting Bloom Filter instead) |
| **Your dataset is small** | A simple `HashSet` is faster, simpler, and exact for small datasets |
| **You need to retrieve the element** | Bloom Filters only answer yes/no — they store no actual data |
| **False positives have severe consequences** | e.g., blocking a legitimate user, financial transactions, medical records |
| **You need to count occurrences** | Bloom Filters only track presence, not frequency |
| **You need to enumerate members** | You cannot list what's in a Bloom Filter |
| **Dynamic size requirements** | Standard Bloom Filters have a fixed size; heavy growth degrades accuracy |

### Key Signal: Avoid a Bloom Filter when...
- Correctness is **non-negotiable** (use a `HashSet` or database)
- The dataset fits **comfortably in memory** already
- You need to **delete, update, or enumerate** elements

---

## 🏗️ Project Structure

```
BloomFilter/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── bloomfilter/
│                   ├── HashUtils.java            # SHA-256 based hash functions
│                   ├── BloomFilter.java           # Core Bloom Filter class
│                   └── BloomFilterExample.java    # Live demo (3 examples)
├── .gitignore
└── README.md
```

---

## 🚀 How to Run

### Prerequisites
- Java 11 or higher

### Compile
```bash
mkdir -p out
javac -d out src/main/java/com/bloomfilter/HashUtils.java \
             src/main/java/com/bloomfilter/BloomFilter.java \
             src/main/java/com/bloomfilter/BloomFilterExample.java
```

### Run
```bash
java -cp out com.bloomfilter.BloomFilterExample
```

---

## 📊 False Positive Rate vs. Bit Array Size

For **1,000 elements** inserted with **k=3** hash functions:

| Bit Array Size | Bits/Element | False Positive Rate |
|---|---|---|
| 1,000 | 1 | ~85.8% ❌ |
| 2,000 | 2 | ~46.9% ❌ |
| 5,000 | 5 | ~9.2% ⚠️ |
| 10,000 | 10 | ~1.7% ✅ |
| 50,000 | 50 | ~0.02% ✅ |
| 100,000 | 100 | ~0.003% ✅ |

> **Rule of thumb:** Use ~10 bits per element for a ~1% false positive rate.

---

## 🔬 Live Example Output

```
BLOOM FILTER — Banned Username Demo

Adding banned usernames: admin, root, superuser, hacker99, spambot

Checking usernames at registration:
  'alice'      : Definitely clean -> allow
  'bob'        : Definitely clean -> allow
  'admin'      : POSSIBLY BANNED  -> block or verify
  'hacker99'   : POSSIBLY BANNED  -> block or verify
  'charlie'    : Definitely clean -> allow
  'root'       : POSSIBLY BANNED  -> block or verify
  'dave123'    : Definitely clean -> allow
  'spambot'    : POSSIBLY BANNED  -> block or verify
  'eve'        : Definitely clean -> allow

Blocked: 4  |  Allowed: 5
Estimated False Positive Rate: 0.002581%
```

---

## 📚 References

- [Original Paper — Burton H. Bloom (1970)](https://dl.acm.org/doi/10.1145/362686.362692)
- [Wikipedia — Bloom Filter](https://en.wikipedia.org/wiki/Bloom_filter)
- [Java BitSet Documentation](https://docs.oracle.com/en/java/se/11/docs/api/java.base/java/util/BitSet.html)

---

## 👤 Author

**Jatin Ghataliya** — [prajapati.jatin94@gmail.com](mailto:prajapati.jatin94@gmail.com)
