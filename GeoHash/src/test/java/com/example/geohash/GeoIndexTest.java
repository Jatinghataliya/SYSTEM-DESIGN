package com.example.geohash;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GeoIndexTest {

    @Test
    void haversineKnownDistance() {
        // Paris -> London is roughly 344 km.
        double d = GeoIndex.haversineMeters(48.8566, 2.3522, 51.5074, -0.1278);
        assertEquals(344_000, d, 3_000);
    }

    @Test
    void precisionShrinksAsRadiusGrows() {
        int small = GeoIndex.precisionForRadius(100, 48.85);
        int large = GeoIndex.precisionForRadius(50_000, 48.85);
        assertTrue(small > large);
    }

    @Test
    void findsOnlyPlacesInsideRadiusSortedByDistance() {
        GeoIndex index = new GeoIndex(GeoIndex.precisionForRadius(3_000, 48.853));
        index.add(new GeoIndex.Place("far",   35.6586, 139.7454));
        index.add(new GeoIndex.Place("mid",   48.8606, 2.3376));
        index.add(new GeoIndex.Place("near",  48.8535, 2.3490));
        index.add(new GeoIndex.Place("out",   48.8049, 2.1204));

        List<GeoIndex.Hit> hits = index.nearby(48.853, 2.3499, 3_000);

        assertEquals(List.of("near", "mid"), hits.stream().map(h -> h.place().id()).toList());
        assertTrue(hits.get(0).distanceMeters() < hits.get(1).distanceMeters());
    }

    @Test
    void matchesBruteForceOnRandomData() {
        Random rnd = new Random(42);
        double radius = 2_000;
        double qLat = 40.7128, qLon = -74.0060;
        GeoIndex index = new GeoIndex(GeoIndex.precisionForRadius(radius, qLat));
        List<GeoIndex.Place> all = new ArrayList<>();

        for (int i = 0; i < 5_000; i++) {
            GeoIndex.Place p = new GeoIndex.Place("p" + i,
                    qLat + (rnd.nextDouble() - 0.5) * 0.2,
                    qLon + (rnd.nextDouble() - 0.5) * 0.2);
            all.add(p);
            index.add(p);
        }

        List<String> expected = all.stream()
                .map(p -> new GeoIndex.Hit(p, GeoIndex.haversineMeters(qLat, qLon, p.lat(), p.lon())))
                .filter(h -> h.distanceMeters() <= radius)
                .sorted(Comparator.comparingDouble(GeoIndex.Hit::distanceMeters))
                .map(h -> h.place().id())
                .toList();
        List<String> actual = index.nearby(qLat, qLon, radius).stream().map(h -> h.place().id()).toList();

        assertFalse(expected.isEmpty());
        assertEquals(expected, actual);
    }

    @Test
    void rejectsBadPrecision() {
        assertThrows(IllegalArgumentException.class, () -> new GeoIndex(0));
        assertThrows(IllegalArgumentException.class, () -> new GeoIndex(13));
    }
}
