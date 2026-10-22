# Quadtree in System Design

A reference on **Quadtree**: what it is, why it exists, when to use it, its tradeoffs, and what to
use instead. Includes a Core Java implementation of a point-region Quadtree with insertion,
proximity search, and range query.

---

## Table of Contents
1. [What Is a Quadtree?](#1-what-is-a-quadtree)
2. [What Is Its Purpose?](#2-what-is-its-purpose)
3. [Why Do We Use It?](#3-why-do-we-use-it)
4. [When Should We Use It?](#4-when-should-we-use-it)
5. [Tradeoffs](#5-tradeoffs)
6. [Advantages and Disadvantages](#6-advantages-and-disadvantages)
7. [Alternatives](#7-alternatives)
8. [Quick Decision Guide](#8-quick-decision-guide)
9. [Real-World Usage](#9-real-world-usage)
10. [Java Implementation](#10-java-implementation)
11. [Running the Project](#11-running-the-project)

---

## 1. What Is a Quadtree?

A **Quadtree** is a tree data structure in which every internal node has exactly **four children**,
each representing one quadrant of a two-dimensional space:

```
+--------+--------+
|        |        |
|  NW    |  NE    |
|        |        |
+--------+--------+
|        |        |
|  SW    |  SE    |
|        |        |
+--------+--------+
```

The tree recursively subdivides a bounding rectangle until each cell contains at most a fixed
number of points (the **capacity**). When a cell overflows it is split into four equal quadrants
and its points are pushed down.

### How it works

1. Start with a root node covering the whole space.
2. Insert a point. If the node has room (count < capacity) store the point here.
3. If the node is full, **subdivide** into NW / NE / SW / SE children and redistribute.
4. Repeat recursively until the point fits in a leaf.

### Key property: adaptive density

Cells in dense regions subdivide many times, producing tiny cells; sparse regions stay as large
cells. The tree depth reflects the data density, not a fixed grid.

```
city centre (many points):
  root -> ... -> leaf (38 m × 38 m)

empty countryside (few points):
  root -> leaf (20 000 km × 10 000 km)
```

---

## 2. What Is Its Purpose?

To provide **efficient 2D spatial indexing** that:

- answers "what points lie within this rectangle?" in O(log n) instead of O(n);
- answers "what are the k nearest neighbours?" by pruning far-away quadrants;
- adapts the cell size automatically to data density.

---

## 3. Why Do We Use It?

- **Density-adaptive.** Dense urban centres get fine cells; empty rural areas stay coarse. A fixed
  grid (GeoHash) wastes space or loses precision when density varies.
- **Efficient range queries.** Searching a Quadtree prunes whole quadrants that cannot intersect
  the query rectangle, giving sub-linear performance.
- **Simple to implement.** The recursive structure maps naturally to code — no library required.
- **In-memory friendliness.** Works well as an in-process index when the dataset fits in RAM.
- **Hierarchical structure.** Naturally supports zoom-in / zoom-out operations.

---

## 4. When Should We Use It?

**Use it when:**

- You need **in-memory spatial indexing** and the dataset fits in RAM.
- **Data density is uneven**: cities vs. oceans, events vs. empty desert.
- You need **range queries** (all points inside a rectangle) or **k-nearest-neighbour** queries.
- You want a **density-aware grid** for rendering maps (each visible tile is one leaf node).
- You are building spatial indexing **from scratch** without a database.

**Do NOT use it when:**

- The dataset is **too large for memory** — use a database with a spatial index (PostGIS, MongoDB).
- You need **exact geodesic distance** on a sphere — Quadtrees operate on a flat 2D plane.
- You need **shard keys** for a distributed database — GeoHash or H3 give a flat string key.
- **Write rate is very high** — every insert may trigger rebalancing across multiple levels.
- You need **polygon containment** — use R-tree or the Ray-Casting algorithm.

---

## 5. Tradeoffs

| Tradeoff | Explanation |
| :--- | :--- |
| **Adaptive vs. fixed grid** | Quadtree cells vary in size; comparing across cells requires care. GeoHash cells are fixed and easy to compare. |
| **Memory vs. disk** | Quadtrees are natural in-memory trees; serialising to disk/database is non-trivial compared to a flat GeoHash key. |
| **Query speed vs. update cost** | Range queries are fast, but inserting a point may cascade splits up several levels. |
| **Precision vs. tree depth** | Very dense data produces very deep trees; a maximum-depth limit risks overcrowding leaf nodes. |
| **Simplicity vs. completeness** | A point-region Quadtree is simple; supporting polygons or lines requires a region/MX Quadtree, which is significantly more complex. |
| **In-memory vs. distributed** | Quadtrees are hard to shard across machines without a complex protocol. |

---

## 6. Advantages and Disadvantages

### Advantages

| | |
| :--- | :--- |
| **Density-adaptive** | Automatically fine-grained where data is dense |
| **Fast range queries** | Prunes whole quadrants; O(log n + k) where k = results |
| **Hierarchical** | Natural zoom levels for map tiles and LOD rendering |
| **No extra library** | ~150 lines of code, pure Java |
| **Flexible capacity** | Tune the leaf capacity for your access pattern |
| **Deletable** | Nodes can be merged back when points are removed |

### Disadvantages

| | |
| :--- | :--- |
| **In-memory only** | Difficult to persist or distribute |
| **Flat-plane geometry** | No built-in geodesic (spherical) distance; works best on a projected plane |
| **Rebalancing cost** | Splits cascade; worst case O(n) for a single insert in a degenerate tree |
| **Not a shard key** | Cannot be used as a database partition key the way GeoHash can |
| **Complex deletion** | Removing a point and merging nodes requires careful bookkeeping |
| **Boundary duplication** | Points exactly on a split axis must be assigned to one side by convention |

---

## 7. Alternatives

| Alternative | Cell shape | Strength | Weakness | Use when |
| :--- | :--- | :--- | :--- | :--- |
| **GeoHash** | Rectangle (fixed) | Flat string key, DB-indexable, sharding key | Fixed grid; boundary problem | DB proximity queries, caching by area |
| **H3** (Uber) | Hexagon | Uniform area, equal neighbours | Library required, complex | Analytics, ride-hailing, heatmaps |
| **S2** (Google) | Spherical quad | Accurate on a sphere, handles poles | Complex, library required | Global geofencing |
| **R-tree / GiST** | Bounding boxes | Exact polygon queries, DB-native | Needs PostGIS / SQLite R*Tree | Polygon containment, exact range |
| **k-d tree** | Axis-aligned splits | Excellent k-NN | Hard to update; in-memory only | Static nearest-neighbour |
| **PostGIS / MongoDB 2dsphere** | S2 / R-tree | Full spatial SQL | Tied to specific database | You already run that database |

Related modules: [GeoHash](../GeoHash/README.md) · [Ray-Casting](../Ray-Casting/README.md)

---

## 8. Quick Decision Guide

```
In-memory index, uneven data density?                     -> Quadtree
Need a DB / cache shard key by location?                  -> GeoHash
Need uniform-area cells for analytics?                    -> H3
Need global coverage or polar regions?                    -> S2
Need exact polygon containment or rich spatial SQL?       -> PostGIS / R-tree
Static dataset, nearest-neighbour only?                   -> k-d tree
Already on Redis?                                         -> Redis GEO (geohash)
```

---

## 9. Real-World Usage

| System | How Quadtree is used |
| :--- | :--- |
| **Google Maps / OpenStreetMap** | Tile pyramids use a quad-key scheme (a Quadtree path) to name tiles at each zoom level |
| **Game engines** | Spatial partitioning for collision detection; only nearby objects are tested |
| **Ride-hailing (early Uber)** | In-memory Quadtree to find available drivers near a pickup point |
| **Computer graphics** | Frustum culling, ray tracing acceleration |
| **Image compression** | Quadtree image compression splits high-detail regions further |
| **Autonomous vehicles** | LIDAR point-cloud segmentation |
| **Cartography** | Generalisation — a leaf with few points is rendered simply |

---

## 10. Java Implementation

Core Java 17, no runtime dependencies. Tests use JUnit 5.

| File | Purpose |
| :--- | :--- |
| [`Point.java`](src/main/java/com/example/quadtree/Point.java) | Immutable 2D point with a label |
| [`BoundingBox.java`](src/main/java/com/example/quadtree/BoundingBox.java) | Axis-aligned rectangle; `contains`, `intersects`, `subdivide` |
| [`QuadTree.java`](src/main/java/com/example/quadtree/QuadTree.java) | Point-region Quadtree: `insert`, `query` (range), `findNearest` |
| [`Main.java`](src/main/java/com/example/quadtree/Main.java) | Demo: insert 1 000 random cities, range query, nearest-neighbour |
| [`QuadTreeTest.java`](src/test/java/com/example/quadtree/QuadTreeTest.java) | Insert/query correctness, boundary cases, k-NN, stress test |

### Insert algorithm

```
insert(point, node):
    if point not in node.bounds: return false
    if node has capacity: store point; return true
    if node has no children: subdivide()
    for child in [NW, NE, SW, SE]:
        if child.insert(point): return true
```

### Range query algorithm

```
query(rect, node, results):
    if rect does not intersect node.bounds: return   // prune
    for point in node.points:
        if rect.contains(point): results.add(point)
    if node has children:
        for child in [NW, NE, SW, SE]:
            query(rect, child, results)
```

### Sample output

```
Inserted 1 000 points into Quadtree (capacity 4)

Range query  [40°N–42°N, 73°W–71°W]  (approx. New York metro)
  Found 23 points in 0.12 ms

Nearest neighbour to (48.85°, 2.35°)  (Paris)
  Closest: "Paris-Centre"  at 0.003°
  Query time: 0.08 ms

Tree stats:
  Total nodes : 847
  Max depth   : 11
  Leaf nodes  : 634
```

---

## 11. Running the Project

Requires JDK 17+ and Maven 3.8+.

```bash
cd Quadtree
mvn test               # unit tests
mvn compile exec:java  # run the demo
```
