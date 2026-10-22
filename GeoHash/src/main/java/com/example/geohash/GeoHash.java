package com.example.geohash;

import java.util.EnumMap;
import java.util.Map;

/**
 * GeoHash encoder / decoder (Gustavo Niemeyer, 2008).
 *
 * <p>The world is repeatedly bisected: longitude first, then latitude, alternating. Each bisection
 * yields one bit (0 = lower half, 1 = upper half). Every 5 bits are written as one base-32 character,
 * so a longer hash is always a refinement of (and shares its prefix with) a shorter one.
 */
public final class GeoHash {

    public static final int MAX_PRECISION = 12;

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    /** The 8 neighbouring cells, as (latitude step, longitude step). */
    public enum Direction {
        N(1, 0), NE(1, 1), E(0, 1), SE(-1, 1), S(-1, 0), SW(-1, -1), W(0, -1), NW(1, -1);

        final int dLat;
        final int dLon;

        Direction(int dLat, int dLon) {
            this.dLat = dLat;
            this.dLon = dLon;
        }
    }

    /** The rectangle covered by a geohash cell. */
    public record Bounds(double minLat, double maxLat, double minLon, double maxLon) {
        public double centerLat() { return (minLat + maxLat) / 2; }
        public double centerLon() { return (minLon + maxLon) / 2; }
        public double height()    { return maxLat - minLat; }
        public double width()     { return maxLon - minLon; }
    }

    private GeoHash() { }

    /** Encodes a coordinate into a geohash of the given length (1..12). */
    public static String encode(double lat, double lon, int precision) {
        if (lat < -90 || lat > 90)   throw new IllegalArgumentException("lat out of range: " + lat);
        if (lon < -180 || lon > 180) throw new IllegalArgumentException("lon out of range: " + lon);
        if (precision < 1 || precision > MAX_PRECISION) {
            throw new IllegalArgumentException("precision must be 1.." + MAX_PRECISION);
        }

        double minLat = -90, maxLat = 90, minLon = -180, maxLon = 180;
        StringBuilder hash = new StringBuilder(precision);
        boolean lonTurn = true;     // first bit always splits longitude
        int bit = 0;
        int index = 0;

        while (hash.length() < precision) {
            if (lonTurn) {
                double mid = (minLon + maxLon) / 2;
                if (lon >= mid) { index = (index << 1) | 1; minLon = mid; }
                else            { index <<= 1;              maxLon = mid; }
            } else {
                double mid = (minLat + maxLat) / 2;
                if (lat >= mid) { index = (index << 1) | 1; minLat = mid; }
                else            { index <<= 1;              maxLat = mid; }
            }
            lonTurn = !lonTurn;

            if (++bit == 5) {
                hash.append(BASE32.charAt(index));
                bit = 0;
                index = 0;
            }
        }
        return hash.toString();
    }

    /** Decodes a geohash into the bounding box of its cell. */
    public static Bounds decode(String hash) {
        if (hash == null || hash.isEmpty()) throw new IllegalArgumentException("empty geohash");

        double minLat = -90, maxLat = 90, minLon = -180, maxLon = 180;
        boolean lonTurn = true;

        for (char c : hash.toCharArray()) {
            int value = BASE32.indexOf(Character.toLowerCase(c));
            if (value < 0) throw new IllegalArgumentException("invalid geohash character: " + c);

            for (int mask = 16; mask > 0; mask >>= 1) {
                boolean upper = (value & mask) != 0;
                if (lonTurn) {
                    double mid = (minLon + maxLon) / 2;
                    if (upper) minLon = mid; else maxLon = mid;
                } else {
                    double mid = (minLat + maxLat) / 2;
                    if (upper) minLat = mid; else maxLat = mid;
                }
                lonTurn = !lonTurn;
            }
        }
        return new Bounds(minLat, maxLat, minLon, maxLon);
    }

    /**
     * Returns the adjacent cell in the given direction, same precision. Longitude wraps around the
     * antimeridian; returns {@code null} when stepping past a pole (no cell exists there).
     */
    public static String neighbor(String hash, Direction direction) {
        Bounds b = decode(hash);
        double lat = b.centerLat() + direction.dLat * b.height();
        double lon = b.centerLon() + direction.dLon * b.width();

        if (lat < -90 || lat > 90) return null;
        if (lon > 180)  lon -= 360;
        if (lon < -180) lon += 360;
        return encode(lat, lon, hash.length());
    }

    /** All 8 neighbours. Entries are missing for directions that fall off a pole. */
    public static Map<Direction, String> neighbors(String hash) {
        Map<Direction, String> result = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) {
            String n = neighbor(hash, d);
            if (n != null) result.put(d, n);
        }
        return result;
    }
}
