package com.example.raycasting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellIndexTest {

    private static GeofenceService exact;
    private static S2GeofenceIndex s2;
    private static H3GeofenceIndex h3;

    @BeforeAll
    static void setUp() {
        exact = CellIndexDemo.baseline();
        s2 = new S2GeofenceIndex();
        h3 = new H3GeofenceIndex(CellIndexDemo.H3_RESOLUTION);
        CellIndexDemo.register(s2);
        CellIndexDemo.register(h3);
    }

    @Test
    void s2AndH3FindTheSameZonesAsRayCastingForClearCases() {
        Point[] points = {
                new Point(77.55, 12.94), new Point(77.65, 13.02), new Point(77.54, 13.03),
                new Point(77.78, 12.97), new Point(77.40, 12.50)
        };
        for (Point p : points) {
            assertEquals(exact.findZone(p), s2.findZone(p), "S2 mismatch at " + p);
            assertEquals(exact.findZone(p), h3.findZone(p), "H3 mismatch at " + p);
        }
    }

    @Test
    void concaveNotchIsNotInZoneL() {
        // x < 77.60 and y > 12.98 is the L's missing corner, which belongs to Zone-NorthWest instead
        Point notch = new Point(77.55, 13.02);
        assertEquals("Zone-NorthWest", exact.findZone(notch).orElseThrow());
        assertEquals("Zone-NorthWest", s2.findZone(notch).orElseThrow());
        assertEquals("Zone-NorthWest", h3.findZone(notch).orElseThrow());
    }

    @Test
    void s2IsAlmostExactAndH3IsApproximateOnRandomPoints() {
        Random rnd = new Random(3);
        int n = 20_000, s2Match = 0, h3Match = 0;
        for (int i = 0; i < n; i++) {
            Point p = new Point(77.45 + rnd.nextDouble() * 0.45, 12.85 + rnd.nextDouble() * 0.30);
            var truth = exact.findZone(p);
            if (truth.equals(s2.findZone(p))) s2Match++;
            if (truth.equals(h3.findZone(p))) h3Match++;
        }
        // S2 differs only where great-circle edges deviate from straight lat/lng lines.
        assertTrue(s2Match >= n * 0.999, "S2 agreement too low: " + s2Match);
        // H3 differs within roughly one cell of a border.
        assertTrue(h3Match >= n * 0.98, "H3 agreement too low: " + h3Match);
    }

    @Test
    void s2AnswersMostPointsFromInteriorCellsWithoutGeometry() {
        long before = s2.interiorHits();
        for (int i = 0; i < 1000; i++) {
            s2.findZone(new Point(77.78, 12.97)); // deep inside Zone-East
        }
        assertEquals(1000, s2.interiorHits() - before);
    }

    @Test
    void h3NeighbourhoodQueryFindsAdjacentZone() {
        // A point just outside Zone-East but within a few cells of it.
        Point justWest = new Point(77.7185, 12.97);
        assertTrue(h3.zonesNear(justWest, 3).contains("Zone-East"));
    }
}
