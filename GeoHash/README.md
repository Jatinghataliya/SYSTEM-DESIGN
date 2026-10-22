# GeoHash in System Design

A reference on **GeoHash**: what it is, why it exists, when to use it, its tradeoffs, and what to use
instead. Includes a Core Java encoder/decoder, neighbour calculation and a "find places near me" index.

---

## Table of Contents
1. [What Is GeoHash?](#1-what-is-geohash)
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

## 1. What Is GeoHash?

**GeoHash** (Gustavo Niemeyer, 2008) is a way to encode a latitude/longitude pair into a short
string such as `u09tunqu`. Each string names a **rectangular cell** on the map; the longer the
string, the smaller the cell.

### How it works

1. Start with the whole world: latitude `[-90, 90]`, longitude `[-180, 180]`.
2. Bisect **longitude**. If the point is in the upper half write `1`, otherwise `0`. Keep that half.
3. Bisect **latitude** the same way.
4. Alternate longitude / latitude, collecting bits.
5. Every **5 bits** are written as one character of the base-32 alphabet
   `0123456789bcdefghjkmnpqrstuvwxyz` (no `a`, `i`, `l`, `o`).

```
Point: (48.8584, 2.2945)  Eiffel Tower

lon bit 1: 2.29 in [-180,180] -> upper -> 1
lat bit 2: 48.8 in [-90, 90]  -> upper -> 1
lon bit 3: ...                         -> 0
...
11010 00000 ... -> base32 -> "u09tunqu"
```

### The key property: shared prefix

A longer geohash is always **inside** the shorter one that is its prefix.

```
u          ~ 5000 km x 5000 km
u09        ~  150 km x  100 km   (Paris region)
u09tu      ~    5 km x    3 km
u09tunqu   ~   19 m  x   25 m
```

Two nearby points usually share a long prefix, so "nearby" becomes a **string prefix match**, which
every database and key-value store can index.

### Precision table (at the equator)

| Length | Cell size (approx.) | Typical use |
| :---: | :--- | :--- |
| 1 | 5,000 km x 5,000 km | Continent |
| 2 | 1,250 km x 625 km | Large country |
| 3 | 156 km x 156 km | Region |
| 4 | 39 km x 19.5 km | City |
| 5 | 4.9 km x 4.9 km | District |
| 6 | 1.2 km x 0.61 km | Neighbourhood |
| 7 | 153 m x 153 m | Street block |
| 8 | 38 m x 19 m | Building |
| 9 | 4.8 m x 4.8 m | Room |
| 10 | 1.2 m x 0.6 m | Parking space |
| 12 | 3.7 cm x 1.9 cm | Survey grade |

Cells get narrower away from the equator because longitude degrees shrink.

---

## 2. What Is Its Purpose?

To turn a **two-dimensional** location into a **one-dimensional** key so that:

- locations can be stored in an ordinary index (B-tree, hash, sorted set);
- "what is near this point?" becomes a few key or prefix lookups instead of a scan of every row;
- data can be **sharded / partitioned by area**.

---

## 3. Why Do We Use It?

- **Indexing 2D data with 1D tools.** Latitude and longitude are two columns; a normal index
  handles one range well, not two. A geohash column handles both with one index.
- **Cheap proximity search.** Search = centre cell + 8 neighbours, then an exact distance filter.
- **Natural sharding key.** Partition a table or cache by geohash prefix so one region maps to
  one shard.
- **Privacy by truncation.** Storing 5 characters reveals "somewhere in this 5 km square" instead of
  an exact address.
- **Compact and human-readable.** 8 characters pin a location to ~20 m; easy to log, debug, put in a
  URL.
- **Grouping / aggregation.** `GROUP BY LEFT(geohash, 5)` produces a heatmap with no GIS library.

---

## 4. When Should We Use It?

Use it when:

- You need **"nearby" queries** on millions of points (drivers, restaurants, friends, stores).
- You are on a store with **no spatial index**: Redis, DynamoDB, Cassandra, plain SQL, Elasticsearch
  keyword fields.
- You want to **shard or cache by location**.
- **Approximate** areas are fine and you can post-filter with exact distance.
- You want to **bucket data for analytics / heatmaps**.

Do **not** use it when:

- You need **exact polygon containment** (geofencing with irregular shapes). See the
  [Ray-Casting](../Ray-Casting/README.md) module.
- You need **uniform cell size** at all latitudes (use H3).
- You need **true distance ordering** without a refinement step.
- Your database already has a spatial index (PostGIS GiST, MongoDB 2dsphere) and you do not need to
  shard by cell.
- Your data crosses the poles or antimeridian a lot and you cannot handle wrap-around.

---

## 5. Tradeoffs

| Tradeoff | Explanation |
| :--- | :--- |
| **Precision vs. cell count** | Long hash = small cell = more cells to scan for a large radius. Short hash = few cells but many false positives. |
| **Simplicity vs. accuracy** | Rectangles approximate circles badly. You must filter with an exact distance (haversine). |
| **Prefix match vs. proximity** | A shared prefix implies closeness, but closeness does **not** imply a shared prefix (see boundary problem). |
| **Uniform grid vs. uniform area** | Cells are the same in degrees, not in metres. Near the poles they are very narrow. |
| **Fixed grid vs. density** | One precision does not suit both a dense city and an empty desert. |

### The boundary problem

Two points 2 metres apart can sit on either side of a cell edge and have **completely different
prefixes**. For example, on the prime meridian:

```
lat = 10, lon = -0.00001 -> eczbzury   (west of the edge)
lat = 10, lon = +0.00001 -> s1b0bh2n   (east of the edge)
```

That is why a proximity query must always search the **centre cell and its 8 neighbours**, never
only the centre cell. This is covered in `GeoHashTest.boundaryProblem_...`.

### Choosing the precision for a radius

Pick the longest geohash whose cell is **at least as wide and tall as the search radius**. Then the
3x3 block of cells is guaranteed to contain the whole search circle.
`GeoIndex.precisionForRadius(radius, lat)` does this.

---

## 6. Advantages and Disadvantages

### Advantages

| | |
| :--- | :--- |
| Simple | ~60 lines of code, no library required |
| Index-friendly | Works with any sorted or hashed index |
| Hierarchical | Truncate to zoom out, append to zoom in |
| Sortable | Lexicographic order roughly follows a Z-order curve (spatial locality) |
| Sharding-friendly | Prefix = natural partition key |
| Compact | One short string instead of two doubles |
| Privacy-friendly | Truncate to blur location |

### Disadvantages

| | |
| :--- | :--- |
| **Boundary problem** | Neighbouring points can have unrelated prefixes; must query 9 cells |
| **Non-uniform cells** | Cell width shrinks toward the poles |
| **Rectangular cells** | Poor circle approximation; needs a distance post-filter |
| **Z-order jumps** | Some adjacent cells are far apart in sort order |
| **Fixed grid** | Cannot adapt to data density |
| **Neighbour edge cases** | Antimeridian wrap-around and poles need special handling |
| **No polygon support** | Only covers rectangles; polygons need many cells |

---

## 7. Alternatives

| Alternative | Cell shape | Strength | Weakness | Use when |
| :--- | :--- | :--- | :--- | :--- |
| **H3** (Uber) | Hexagon | Uniform neighbours (6, all equidistant), good for analytics | More complex, library needed | Surge pricing, heatmaps, ride-hailing |
| **S2** (Google) | Spherical quad | Handles poles / antimeridian, covers regions well | Complex, library needed | Global-scale geofencing |
| **Quadtree** | Adaptive square | Adapts to density | Tree must be maintained, not a flat key | In-memory, uneven density |
| **R-tree / GiST** | Bounding boxes | Exact, rich queries | Needs database or library support | PostGIS, SQLite R*Tree |
| **k-d tree** | Axis-aligned splits | Good nearest-neighbour | Hard to update, in-memory | Static datasets |
| **Redis GEO** | Geohash internally | Built-in `GEOSEARCH` | Redis only (52-bit geohash in a sorted set) | You already run Redis |
| **MongoDB 2dsphere / PostGIS** | S2 / R-tree | Native operators | Tied to that database | You already run that database |
| **Plain lat/lon + bounding-box SQL** | None | No new concept | Two-range scan is slow at scale | Small tables |

Related modules: [Ray-Casting](../Ray-Casting/README.md) covers exact point-in-polygon, S2 and H3.

---

## 8. Quick Decision Guide

```
Need "nearby" on a store without spatial support?          -> GeoHash
Need to shard or cache data by location?                   -> GeoHash prefix
Need uniform cells for analytics / heatmaps?               -> H3
Need global coverage with exact regions?                   -> S2
Need exact polygon containment?                            -> Ray casting / PostGIS
Data density varies wildly?                                -> Quadtree
Already on Redis?                                          -> Redis GEO (uses geohash)
Already on PostgreSQL?                                     -> PostGIS
```

---

## 9. Real-World Usage

| System | How GeoHash is used |
| :--- | :--- |
| **Redis `GEOADD` / `GEOSEARCH`** | Stores a 52-bit geohash as the score of a sorted set |
| **Elasticsearch** | `geohash_grid` aggregation and `geo_point` indexing |
| **DynamoDB / Cassandra** | Geohash as partition or sort key for proximity queries |
| **Ride-hailing / delivery** | Match drivers to riders inside the same and neighbouring cells |
| **Social apps** | "Friends nearby", location tagging with blurred precision |
| **Weather / IoT** | Aggregate sensor readings per cell |
| **Ad targeting** | Target an area without storing exact user location |

---

## 10. Java Implementation

Core Java 17, no runtime dependencies. Tests use JUnit 5.

| File | Purpose |
| :--- | :--- |
| [`GeoHash.java`](src/main/java/com/example/geohash/GeoHash.java) | `encode`, `decode` (to bounding box), `neighbor`, `neighbors`; antimeridian wrap, pole handling |
| [`GeoIndex.java`](src/main/java/com/example/geohash/GeoIndex.java) | In-memory proximity index: 3x3 cell lookup + haversine filter, `precisionForRadius` |
| [`Main.java`](src/main/java/com/example/geohash/Main.java) | Demo: precision table, shared prefixes, neighbours, "near Notre-Dame" search |
| [`GeoHashTest.java`](src/test/java/com/example/geohash/GeoHashTest.java) | Known vectors, round-trip, prefix property, neighbours, antimeridian, poles, boundary problem |
| [`GeoIndexTest.java`](src/test/java/com/example/geohash/GeoIndexTest.java) | Haversine, radius filter, ordering, equivalence with brute force on 5,000 random points |

### Search algorithm

```
nearby(lat, lon, radius):
    1. precision = longest geohash whose cell >= radius
    2. center    = encode(lat, lon, precision)
    3. cells     = center + 8 neighbours
    4. candidates = all places stored under those 9 keys        <- O(1) lookups
    5. keep candidates where haversine(query, place) <= radius  <- removes corner false positives
    6. sort by distance
```

Step 5 is required: a 3x3 block is a square larger than the circle, so corner candidates can be
outside the radius.

### Sample output

```
precision 5  u09tu     cell ~ 4,892 m x 3,219 m
precision 8  u09tunqu  cell ~ 19 m x 25 m

Eiffel Tower : u09tunqu
Louvre       : u09tvny1  (same city -> shares prefix 'u09')
Tokyo Tower  : xn76ggrq  (far away -> different prefix)

Proximity search (radius 5 km around Notre-Dame)
Louvre            1,234 m
Sacre-Coeur       3,780 m
Eiffel Tower      4,097 m
```

---

## 11. Running the Project

Requires JDK 17+ and Maven 3.8+.

```bash
cd GeoHash
mvn test               # unit tests
mvn compile exec:java  # run the demo
```
