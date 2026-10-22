package com.example.geohash;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory "find places near me" index built on geohash prefixes.
 *
 * <p>This is the same idea a database uses when {@code geohash} is stored in an indexed column and
 * queried with {@code WHERE geohash IN (...)} / {@code LIKE 'u4pr%'}: a proximity query becomes up to
 * nine exact key lookups, followed by an exact distance filter to remove the corner false positives.
 */
public final class GeoIndex {

    private static final double EARTH_RADIUS_M = 6_371_008.8;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;

    /** A point of interest. */
    public record Place(String id, double lat, double lon) { }

    /** A search hit with its distance from the query point. */
    public record Hit(Place place, double distanceMeters) { }

    private final int precision;
    private final Map<String, List<Place>> cells = new HashMap<>();

    /** @param precision geohash length used to bucket places; see {@link #precisionForRadius} */
    public GeoIndex(int precision) {
        if (precision < 1 || precision > GeoHash.MAX_PRECISION) {
            throw new IllegalArgumentException("precision must be 1.." + GeoHash.MAX_PRECISION);
        }
        this.precision = precision;
    }

    public void add(Place place) {
        String key = GeoHash.encode(place.lat(), place.lon(), precision);
        cells.computeIfAbsent(key, k -> new ArrayList<>()).add(place);
    }

    /** Returns all places within {@code radiusMeters} of the point, nearest first. */
    public List<Hit> nearby(double lat, double lon, double radiusMeters) {
        String center = GeoHash.encode(lat, lon, precision);

        // The centre cell plus its 8 neighbours cover the radius as long as the cell is at least
        // as large as the radius (guaranteed by precisionForRadius).
        Set<String> keys = new LinkedHashSet<>();
        keys.add(center);
        keys.addAll(GeoHash.neighbors(center).values());

        List<Hit> hits = new ArrayList<>();
        for (String key : keys) {
            for (Place p : cells.getOrDefault(key, List.of())) {
                double d = haversineMeters(lat, lon, p.lat(), p.lon());
                if (d <= radiusMeters) hits.add(new Hit(p, d));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::distanceMeters));
        return hits;
    }

    /**
     * Longest geohash length whose cells are still at least {@code radiusMeters} wide and tall at
     * the given latitude, so a 3x3 block of cells is guaranteed to cover the search circle.
     */
    public static int precisionForRadius(double radiusMeters, double lat) {
        for (int p = GeoHash.MAX_PRECISION; p >= 1; p--) {
            GeoHash.Bounds cell = GeoHash.decode(GeoHash.encode(lat, 0, p));
            double heightM = cell.height() * METERS_PER_DEGREE_LAT;
            double widthM = cell.width() * METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(Math.abs(lat)));
            if (heightM >= radiusMeters && widthM >= radiusMeters) return p;
        }
        return 1;
    }

    /** Great-circle distance in meters. */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
