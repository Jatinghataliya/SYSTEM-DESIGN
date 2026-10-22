package com.example.geohash;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GeoHashTest {

    @Test
    void encodesKnownVectors() {
        assertEquals("ezs42", GeoHash.encode(42.6, -5.6, 5));
        assertEquals("u4pruydqqvj", GeoHash.encode(57.64911, 10.40744, 11));
        assertEquals("6gkzwgjz", GeoHash.encode(-25.382708, -49.265506, 8));
    }

    @Test
    void decodedCellContainsOriginalPoint() {
        double lat = 48.8584, lon = 2.2945;
        for (int p = 1; p <= GeoHash.MAX_PRECISION; p++) {
            GeoHash.Bounds b = GeoHash.decode(GeoHash.encode(lat, lon, p));
            assertTrue(lat >= b.minLat() && lat <= b.maxLat(), "lat at precision " + p);
            assertTrue(lon >= b.minLon() && lon <= b.maxLon(), "lon at precision " + p);
        }
    }

    @Test
    void longerHashHasSmallerCell() {
        GeoHash.Bounds coarse = GeoHash.decode("u09t");
        GeoHash.Bounds fine = GeoHash.decode("u09tu");
        assertTrue(fine.height() * fine.width() < coarse.height() * coarse.width());
    }

    @Test
    void longerHashSharesPrefixWithShorter() {
        String full = GeoHash.encode(35.6586, 139.7454, 10);
        for (int p = 1; p < 10; p++) {
            assertEquals(full.substring(0, p), GeoHash.encode(35.6586, 139.7454, p));
        }
    }

    @Test
    void decodeIsCaseInsensitive() {
        assertEquals(GeoHash.decode("ezs42"), GeoHash.decode("EZS42"));
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> GeoHash.encode(91, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> GeoHash.encode(0, 181, 5));
        assertThrows(IllegalArgumentException.class, () -> GeoHash.encode(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> GeoHash.encode(0, 0, 13));
        assertThrows(IllegalArgumentException.class, () -> GeoHash.decode(""));
        assertThrows(IllegalArgumentException.class, () -> GeoHash.decode("abc"));   // 'a' is not in alphabet
    }

    @Test
    void neighborsAreEightDistinctAdjacentCells() {
        Map<GeoHash.Direction, String> n = GeoHash.neighbors("u09tun");
        assertEquals(8, n.size());
        assertEquals(8, Set8.of(n).size());
        assertFalse(n.containsValue("u09tun"));

        GeoHash.Bounds me = GeoHash.decode("u09tun");
        GeoHash.Bounds north = GeoHash.decode(n.get(GeoHash.Direction.N));
        assertEquals(me.maxLat(), north.minLat(), 1e-9);
        assertEquals(me.minLon(), north.minLon(), 1e-9);
    }

    @Test
    void neighborOfNeighborIsOriginal() {
        String start = "u09tun";
        String east = GeoHash.neighbor(start, GeoHash.Direction.E);
        assertEquals(start, GeoHash.neighbor(east, GeoHash.Direction.W));
    }

    @Test
    void neighborWrapsAcrossAntimeridian() {
        String eastEdge = GeoHash.encode(0.1, 179.99, 4);
        String east = GeoHash.neighbor(eastEdge, GeoHash.Direction.E);
        assertNotNull(east);
        assertTrue(GeoHash.decode(east).minLon() < -179);
    }

    @Test
    void noNeighborBeyondPole() {
        String top = GeoHash.encode(89.99, 10, 4);
        assertNull(GeoHash.neighbor(top, GeoHash.Direction.N));
        assertEquals(5, GeoHash.neighbors(top).size());
    }

    @Test
    void boundaryProblem_closePointsCanHaveDifferentPrefixes() {
        // Two points ~2 m apart on either side of the prime meridian share almost nothing.
        String west = GeoHash.encode(10.0, -0.00001, 8);
        String east = GeoHash.encode(10.0, 0.00001, 8);
        assertNotEquals(west.substring(0, 2), east.substring(0, 2));
        // ...but they are neighbours, which is why queries must also scan neighbouring cells.
        assertEquals(east, GeoHash.neighbor(west, GeoHash.Direction.E));
    }

    /** Tiny helper to count distinct values of a map. */
    private static final class Set8 {
        static java.util.Set<String> of(Map<GeoHash.Direction, String> m) {
            return new java.util.HashSet<>(m.values());
        }
    }
}
