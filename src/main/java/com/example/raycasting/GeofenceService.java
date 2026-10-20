package com.example.raycasting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * System-design pattern: cheap bounding-box filter first, exact ray casting second.
 * With many zones this avoids running the O(n) ray cast against every polygon.
 * (At real scale, replace the loop over zones with an R-tree / geohash / H3 index.)
 */
public final class GeofenceService {

    private final Map<String, RayCastingPolygon> zones = new LinkedHashMap<>();
    private long rayCastCalls = 0;

    public void addZone(String name, RayCastingPolygon polygon) {
        zones.put(name, polygon);
    }

    public Optional<String> findZone(Point p) {
        for (Map.Entry<String, RayCastingPolygon> e : zones.entrySet()) {
            RayCastingPolygon polygon = e.getValue();
            if (!polygon.boundingBox().contains(p)) {
                continue; // O(1) reject
            }
            rayCastCalls++;
            if (polygon.contains(p)) { // O(n) exact check, only for candidates
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    public long rayCastCalls() {
        return rayCastCalls;
    }
}
