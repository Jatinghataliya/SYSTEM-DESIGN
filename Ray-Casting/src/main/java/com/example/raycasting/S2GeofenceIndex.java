package com.example.raycasting;

import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.common.geometry.S2Loop;
import com.google.common.geometry.S2Point;
import com.google.common.geometry.S2Polygon;
import com.google.common.geometry.S2RegionCoverer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Google S2 geofence index.
 *
 * S2 projects the sphere onto a cube and subdivides each face into a quadtree of cells
 * (levels 0..30). Each cell has a 64-bit id, and a parent's id range contains its children.
 *
 * Indexing (once per zone):
 *   - interior covering: cells entirely INSIDE the polygon  -> a hit means "inside", no geometry test
 *   - covering:          cells that cover the polygon       -> a hit is only a candidate,
 *                                                              confirmed with an exact S2Polygon test
 *
 * Query (per point): convert to a leaf cell, walk its ancestors from MAX_LEVEL up to MIN_LEVEL,
 * and look each ancestor up in a hash map. That is O(levels) hash lookups instead of O(n) edges.
 */
public final class S2GeofenceIndex implements ZoneLocator {

    private static final int MIN_LEVEL = 8;   // ~ 20 km cells
    private static final int MAX_LEVEL = 14;  // ~ 300 m cells
    private static final int MAX_CELLS = 128;

    private final S2RegionCoverer coverer = S2RegionCoverer.builder()
            .setMinLevel(MIN_LEVEL)
            .setMaxLevel(MAX_LEVEL)
            .setMaxCells(MAX_CELLS)
            .build();

    private final Map<String, S2Polygon> polygons = new HashMap<>();
    private final Map<S2CellId, String> interiorCells = new HashMap<>();
    private final Map<S2CellId, List<String>> boundaryCells = new HashMap<>();

    private long exactChecks = 0;
    private long interiorHits = 0;

    @Override
    public void addZone(String name, List<Point> vertices) {
        List<S2Point> s2Points = new ArrayList<>();
        for (Point v : vertices) {
            s2Points.add(S2LatLng.fromDegrees(v.y(), v.x()).toPoint()); // S2 takes (lat, lng)
        }
        S2Loop loop = new S2Loop(s2Points);
        loop.normalize(); // S2 requires counter-clockwise orientation (interior on the left)
        S2Polygon polygon = new S2Polygon(loop);
        polygons.put(name, polygon);

        for (S2CellId id : coverer.getInteriorCovering(polygon).cellIds()) {
            interiorCells.put(id, name);
        }
        for (S2CellId id : coverer.getCovering(polygon).cellIds()) {
            boundaryCells.computeIfAbsent(id, k -> new ArrayList<>()).add(name);
        }
    }

    @Override
    public Optional<String> findZone(Point p) {
        S2LatLng latLng = S2LatLng.fromDegrees(p.y(), p.x());
        S2Point s2Point = latLng.toPoint();
        S2CellId leaf = S2CellId.fromPoint(s2Point);

        for (int level = MAX_LEVEL; level >= MIN_LEVEL; level--) {
            S2CellId cell = leaf.parent(level);

            String interior = interiorCells.get(cell);
            if (interior != null) {
                interiorHits++;
                return Optional.of(interior); // cell fully inside the zone: no geometry needed
            }
            List<String> candidates = boundaryCells.get(cell);
            if (candidates != null) {
                for (String zone : candidates) {
                    exactChecks++;
                    if (polygons.get(zone).contains(s2Point)) {
                        return Optional.of(zone); // candidate confirmed by exact test
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The S2 cell token for a point at the given level; handy for sharding / cache keys. */
    public String cellToken(Point p, int level) {
        return S2CellId.fromLatLng(S2LatLng.fromDegrees(p.y(), p.x())).parent(level).toToken();
    }

    public int indexedCellCount() {
        return interiorCells.size() + boundaryCells.size();
    }

    public long exactChecks() {
        return exactChecks;
    }

    public long interiorHits() {
        return interiorHits;
    }
}
