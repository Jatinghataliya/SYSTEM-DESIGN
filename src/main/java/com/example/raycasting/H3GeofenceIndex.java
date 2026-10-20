package com.example.raycasting;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Uber H3 geofence index.
 *
 * H3 tiles the globe with hexagons (plus 12 pentagons) at 16 resolutions (0..15).
 * Each cell has a 64-bit id.
 *
 * Indexing (once per zone): polygonToCells() returns every cell at the chosen resolution whose
 * CENTER lies inside the polygon. Store cell -> zone in a hash map.
 *
 * Query (per point): latLngToCell() then ONE hash lookup. O(1), no geometry at query time.
 *
 * Tradeoff: results are approximate near the zone border (cell granularity). Resolution 9 has
 * hexagons with ~175 m edges, so a point can be misclassified only within about that distance of the border.
 * For exact answers, use the cell as a coarse filter and confirm with ray casting.
 */
public final class H3GeofenceIndex implements ZoneLocator {

    private final H3Core h3;
    private final int resolution;
    private final Map<Long, String> cellToZone = new HashMap<>();

    public H3GeofenceIndex(int resolution) {
        this.resolution = resolution;
        try {
            this.h3 = H3Core.newInstance(); // loads the bundled native library
        } catch (IOException e) {
            throw new IllegalStateException("Could not load the H3 native library", e);
        }
    }

    @Override
    public void addZone(String name, List<Point> vertices) {
        List<LatLng> outer = new ArrayList<>();
        for (Point v : vertices) {
            outer.add(new LatLng(v.y(), v.x())); // H3 takes (lat, lng) in degrees
        }
        for (long cell : h3.polygonToCells(outer, List.of(), resolution)) {
            cellToZone.put(cell, name);
        }
    }

    @Override
    public Optional<String> findZone(Point p) {
        return Optional.ofNullable(cellToZone.get(cellOf(p)));
    }

    public long cellOf(Point p) {
        return h3.latLngToCell(p.y(), p.x(), resolution);
    }

    public String cellAddress(Point p) {
        return h3.latLngToCellAddress(p.y(), p.x(), resolution);
    }

    /**
     * "What zones are within k rings of this point?" gridDisk returns the cell plus all
     * neighbours up to k steps away. This is the operation hexagons are especially good at,
     * because every neighbour is the same distance from the centre.
     */
    public Set<String> zonesNear(Point p, int k) {
        Set<String> zones = new HashSet<>();
        for (long cell : h3.gridDisk(cellOf(p), k)) {
            String zone = cellToZone.get(cell);
            if (zone != null) {
                zones.add(zone);
            }
        }
        return zones;
    }

    public int indexedCellCount() {
        return cellToZone.size();
    }
}
