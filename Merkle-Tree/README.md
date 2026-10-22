# 🌳 Merkle Tree — Java Implementation

A clean, well-documented implementation of the **Merkle Tree** cryptographic data structure in Java, with three live examples covering blockchain transaction batching, tamper detection, and inclusion proofs.

---

## 📖 What is a Merkle Tree?

A **Merkle Tree** (also called a **Hash Tree**) is a **tree of cryptographic hashes** where:

- Every **leaf node** contains the hash of a data block.
- Every **internal node** contains the hash of the concatenation of its children's hashes.
- The **root node** (Merkle Root) represents a single fingerprint of the entire dataset.

It was invented by **Ralph Merkle** and patented in 1979. Today it is a fundamental building block in Bitcoin, Git, IPFS, Certificate Transparency, and many distributed systems.

```
                      [Root Hash]
                     /           \
            [Hash 0-1]           [Hash 2-3]
            /       \            /       \
       [Hash 0]  [Hash 1]  [Hash 2]  [Hash 3]
          |          |         |         |
       [Data 0]  [Data 1]  [Data 2]  [Data 3]
```

> The Merkle Root is a single hash that uniquely and verifiably summarises every piece of data in the tree.

---

## ⚙️ How It Works

### Building the Tree
1. Hash each data block → create **leaf nodes**.
2. Pair consecutive nodes and hash their combined hashes → create **parent nodes**.
3. Repeat until a single **root hash** remains.
4. If the count at any level is odd, the last node is **duplicated** (Bitcoin convention).

### Tamper Detection
Any change to a single leaf propagates upward through every parent hash, changing the root. Comparing two Merkle Roots tells you instantly whether two datasets are identical.

### Inclusion Proof (Audit Proof)
To prove that a specific leaf is in the tree, you only need **O(log n)** sibling hashes — not the entire dataset. The verifier recomputes the root hash from the leaf and the proof and checks it against the known root.

```
Proof for Leaf 2 = [Hash 3,  Hash 0-1,  Hash 4-7]
                        ↑         ↑           ↑
                    sibling   uncle level  upper level
```

---

## 🎯 Purpose

The primary purpose of a Merkle Tree is to provide:

1. **Efficient integrity verification** — detect any change in a large dataset with a single hash comparison.
2. **Compact inclusion proofs** — prove membership with O(log n) hashes instead of O(n) data.
3. **Trustless data synchronisation** — two parties can efficiently find exactly which blocks differ.

---

## ✅ Where You SHOULD Use a Merkle Tree

| Use Case | Why It Fits |
|---|---|
| **Blockchain (Bitcoin, Ethereum)** | Transactions in a block are organised as a Merkle Tree; the root goes into the block header for compact verification |
| **Git version control** | Every commit is a Merkle DAG; Git finds changed files in O(log n) |
| **IPFS / content-addressed storage** | Content IDs are Merkle roots, enabling deduplication and integrity checks |
| **Certificate Transparency (CT Logs)** | Auditors verify inclusion of TLS certificates in O(log n) steps |
| **Distributed databases (Cassandra, DynamoDB)** | Anti-entropy uses Merkle Trees to find out-of-sync replicas efficiently |
| **File synchronisation (rsync-style)** | Quickly identify which file chunks differ between two hosts |
| **Smart contracts / ZK proofs** | Prove membership of a set on-chain with minimal gas cost |
| **Audit logs** | Tamper-evident logs where any modification is immediately detectable |

### Key Signal: Use a Merkle Tree when...
- You need to **detect tampering** in a large dataset efficiently.
- You need **compact, verifiable proofs** of data membership.
- You need to **synchronise** two datasets and find exactly what changed.
- **Trust is distributed** and no single party should control the data.

---

## ❌ Where You Should NOT Use a Merkle Tree

| Situation | Why It Doesn't Fit |
|---|---|
| **Tiny datasets** | A simple list comparison or a single hash is faster and simpler |
| **Frequent updates with random access patterns** | Rebuilding subtrees on every update is expensive; use a Merkle Patricia Trie (Ethereum) instead |
| **You need to query or search data** | A Merkle Tree stores hashes only — not the data itself |
| **Low-latency key-value lookups** | A B-Tree or hash table is far more appropriate |
| **Non-critical integrity checks** | A simple checksum (CRC32, MD5) is cheaper if cryptographic security is not needed |

---

## ⚖️ Trade-offs

| Property | Detail |
|---|---|
| **Space** | O(2n) nodes — roughly double the number of data blocks |
| **Build time** | O(n log n) — one hash per node |
| **Proof size** | O(log n) — only sibling hashes along the path to root |
| **Verification time** | O(log n) — recompute the path from leaf to root |
| **Update cost** | O(log n) — only the affected path needs rehashing |
| **Tamper evidence** | Any change cascades immediately to the root |

---

## ✨ Advantages

- ✅ **O(log n) proofs** — massively more efficient than sending entire datasets
- ✅ **Cryptographically secure** — impossible to forge a valid proof without breaking SHA-256
- ✅ **Incremental updates** — only the path from changed leaf to root needs rehashing
- ✅ **Decentralised trust** — anyone with the root can verify any proof independently
- ✅ **Widely proven** — used in Bitcoin since 2009, battle-tested at planet scale

## ⚠️ Disadvantages

- ❌ **Extra storage** — roughly 2× the number of leaf nodes stored as hashes
- ❌ **Not a database** — you cannot query or retrieve data, only verify hashes
- ❌ **Static structure** — deletion requires careful rebalancing or tombstone patterns
- ❌ **Hash function dependency** — security is only as strong as the underlying hash (SHA-256 is standard)

---

## 🔄 Alternatives

| Alternative | When to Prefer It |
|---|---|
| **Simple checksum (SHA-256 of entire file)** | You only need whole-file integrity, not partial proofs |
| **Merkle Patricia Trie (Ethereum)** | You need key-value storage with Merkle proofs (modified Merkle Tree) |
| **B-Tree / LSM-Tree** | You need full database capabilities (search, range queries) |
| **Accumulator (RSA, BLS)** | O(1) membership proofs with constant-size witnesses (advanced cryptography) |
| **Sparse Merkle Tree** | You need non-membership (exclusion) proofs as well as inclusion proofs |
| **Vector Commitments** | You need to update individual elements with constant-size update proofs |

---

## 🏗️ Project Structure

```
Merkle-Tree/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── merkletree/
│                   ├── HashUtils.java          # SHA-256 utility
│                   ├── MerkleNode.java          # Leaf and internal node
│                   ├── MerkleTree.java          # Build, proof generation, verification
│                   └── MerkleTreeExample.java   # Live demo (3 examples)
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
javac -d out src/main/java/com/merkletree/HashUtils.java \
             src/main/java/com/merkletree/MerkleNode.java \
             src/main/java/com/merkletree/MerkleTree.java \
             src/main/java/com/merkletree/MerkleTreeExample.java
```

### Run
```bash
java -cp out com.merkletree.MerkleTreeExample
```

---

## 🔬 Live Example Output

```
============================================================
DEMO 1 — Blockchain Transaction Batch
============================================================

Transactions in block:
  [0] Alice -> Bob : 1.5 BTC
  [1] Bob -> Charlie : 0.8 BTC
  [2] Charlie -> Dave : 2.0 BTC
  [3] Dave -> Eve : 0.3 BTC
  [4] Eve -> Alice : 1.1 BTC
  [5] Frank -> Grace : 0.5 BTC

Merkle Root: 83107b9bf207e2af60dc0c2f450a21cd69be012cb2caf1b9f1455cb3e40b33f2

=== Merkle Tree ===
├── Node[hash=83107b9b...]
│   ├── Node[hash=87c2bd58...]
│   │   ├── Node[hash=bee354cd...]
│   │   │   ├── Leaf[data=Alice -> Bob : 1.5 BTC, hash=4e1f87da...]
│   │   │   └── Leaf[data=Bob -> Charlie : 0.8 BTC, hash=d71ef05d...]
│   │   └── Node[hash=c92534bc...]
│   │       ├── Leaf[data=Charlie -> Dave : 2.0 BTC, hash=ea54a77c...]
│   │       └── Leaf[data=Dave -> Eve : 0.3 BTC, hash=3fbe1228...]
│   └── Node[hash=2338d562...]
│       ├── Node[hash=5f31f449...]
│       │   ├── Leaf[data=Eve -> Alice : 1.1 BTC, hash=116ae2c6...]
│       │   └── Leaf[data=Frank -> Grace : 0.5 BTC, hash=a799bb98...]
│       └── ...

============================================================
DEMO 2 — Tamper Detection
============================================================

Original Merkle Root : d2999dcf3dd92ae5697e460b671402977b1b40fc2869e9f93e202027216bc2ad
Tampered Merkle Root : d68026d87e8f2ba5ef680c82cd63b9a716301630fe4bd918c49a1833016e9a7a

⚠️  TAMPER DETECTED — roots do NOT match!

============================================================
DEMO 3 — Inclusion Proof (Audit Proof)
============================================================

Merkle Root: ce44ca3930a27580ce3e9d3862119dca2e958eb0747b00933e27e5ad4e5cbcdb

Proving inclusion of: "invoice_2024_02.pdf" (index 2)
Proof steps (3 hashes needed):
  Step 1: RIGHT:290b42a2...
  Step 2: LEFT:005b0d59...
  Step 3: RIGHT:be2f0247...

Proof verification: ✅ VALID — leaf is in the tree
Tampered data verification: ❌ REJECTED — tampered data detected
```

---

## 📚 References

- [Original Patent — Ralph Merkle (1979)](https://patents.google.com/patent/US4309569A)
- [Bitcoin Whitepaper — Satoshi Nakamoto (2008)](https://bitcoin.org/bitcoin.pdf)
- [Wikipedia — Merkle Tree](https://en.wikipedia.org/wiki/Merkle_tree)
- [Certificate Transparency RFC 6962](https://tools.ietf.org/html/rfc6962)

---

## 👤 Author

**Jatin Ghataliya** — [prajapati.jatin94@gmail.com](mailto:prajapati.jatin94@gmail.com)
