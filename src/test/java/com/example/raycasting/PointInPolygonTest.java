package com.example.raycasting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PointInPolygonTest {

    private static final List<Point> L_SHAPE = List.of(
            new Point(0, 0), new Point(6, 0), new Point(6, 2),
            new Point(2, 2), new Point(2, 6), new Point(0, 6));

    private final RayCastingPolygon own = new RayCastingPolygon(L_SHAPE);

    @Test
    void insideAndOutsideOnConcavePolygon() {
        assertTrue(own.contains(new Point(1, 1)));
        assertTrue(own.contains(new Point(4, 1)));
        assertFalse(own.contains(new Point(4, 4)), "concave notch is outside");
        assertFalse(own.contains(new Point(7, 1)));
        assertFalse(own.contains(new Point(-1, 3)));
    }

    @Test
    void rayThroughVertexIsCountedOnce() {
        // y = 2 passes exactly through vertices (6,2) and (2,2)
        assertTrue(own.contains(new Point(1, 2)));
        assertFalse(own.contains(new Point(7, 2)));
    }

    @Test
    void rejectsDegeneratePolygon() {
        assertThrows(IllegalArgumentException.class,
                () -> new RayCastingPolygon(List.of(new Point(0, 0), new Point(1, 1))));
    }

    @Test
    void agreesWithJtsAndAwtOnRandomPoints() {
        JtsPointInPolygon jts = new JtsPointInPolygon(L_SHAPE);
        AwtPointInPolygon awt = new AwtPointInPolygon(L_SHAPE);
        Random rnd = new Random(7);
        for (int i = 0; i < 10_000; i++) {
            // non-integer coordinates avoid exact-boundary hits
            Point p = new Point(rnd.nextDouble() * 10 - 2, rnd.nextDouble() * 10 - 2);
            boolean expected = own.contains(p);
            assertEquals(expected, jts.contains(p), "JTS mismatch at " + p);
            assertEquals(expected, jts.containsRayCrossing(p), "JTS ray crossing mismatch at " + p);
            assertEquals(expected, awt.contains(p), "AWT mismatch at " + p);
        }
    }

    @Test
    void geofenceServiceUsesBoundingBoxToSkipRayCasts() {
        GeofenceService service = new GeofenceService();
        service.addZone("A", new RayCastingPolygon(List.of(
                new Point(0, 0), new Point(10, 0), new Point(10, 10), new Point(0, 10))));
        service.addZone("B", new RayCastingPolygon(List.of(
                new Point(20, 0), new Point(30, 0), new Point(30, 10), new Point(20, 10))));

        assertEquals("B", service.findZone(new Point(25, 5)).orElseThrow());
        assertEquals(1, service.rayCastCalls(), "zone A rejected by bounding box");
        assertTrue(service.findZone(new Point(100, 100)).isEmpty());
        assertEquals(1, service.rayCastCalls(), "far point rejected by both boxes");
    }
}
