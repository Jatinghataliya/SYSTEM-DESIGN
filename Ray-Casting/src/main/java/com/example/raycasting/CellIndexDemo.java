package com.example.raycasting;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Compares exact ray casting against the S2 and H3 cell indexes on real-looking coordinates
 * (roughly Bengaluru). Points are (longitude, latitude).
 */
public final class CellIndexDemo {

    static final List<Point> ZONE_L = List.of(   // concave L shape
            new Point(77.50, 12.90), new Point(77.70, 12.90), new Point(77.70, 13.05),
            new Point(77.60, 13.05), new Point(77.60, 12.98), new Point(77.50, 12.98));
    static final List<Point> ZONE_EAST = List.of(
            new Point(77.72, 12.90), new Point(77.85, 12.90),
            new Point(77.85, 13.05), new Point(77.72, 13.05));
    static final List<Point> ZONE_NORTH_WEST = List.of(
            new Point(77.50, 13.00), new Point(77.58, 13.00),
            new Point(77.58, 13.10), new Point(77.50, 13.10));

    static final int H3_RESOLUTION = 9;

    private CellIndexDemo() {
    }

    /** Exact baseline: bounding-box filter + ray casting. */
    static GeofenceService baseline() {
        GeofenceService service = new GeofenceService();
        service.addZone("Zone-L", new RayCastingPolygon(ZONE_L));
        service.addZone("Zone-East", new RayCastingPolygon(ZONE_EAST));
        service.addZone("Zone-NorthWest", new RayCastingPolygon(ZONE_NORTH_WEST));
        return service;
    }

    static void register(ZoneLocator locator) {
        locator.addZone("Zone-L", ZONE_L);
        locator.addZone("Zone-East", ZONE_EAST);
        locator.addZone("Zone-NorthWest", ZONE_NORTH_WEST);
    }

    public static void run() {
        GeofenceService exact = baseline();
        S2GeofenceIndex s2 = new S2GeofenceIndex();
        H3GeofenceIndex h3 = new H3GeofenceIndex(H3_RESOLUTION);
        register(s2);
        register(h3);

        System.out.println("Index sizes: S2 cells=" + s2.indexedCellCount()
                + ", H3 cells (res " + H3_RESOLUTION + ")=" + h3.indexedCellCount());

        System.out.println();
        System.out.println("-- Sample lookups --");
        System.out.printf("%-18s %-16s %-16s %-16s%n", "Point (lng,lat)", "Ray casting", "S2", "H3");
        Point[] samples = {
                new Point(77.55, 12.94),  // Zone-L, vertical arm
                new Point(77.65, 13.02),  // Zone-L, horizontal arm
                new Point(77.54, 13.03),  // Zone-L's notch, inside Zone-NorthWest
                new Point(77.78, 12.97),  // Zone-East
                new Point(77.40, 12.50)   // nowhere
        };
        for (Point p : samples) {
            System.out.printf("%-18s %-16s %-16s %-16s%n",
                    "(" + p.x() + "," + p.y() + ")",
                    name(exact.findZone(p)), name(s2.findZone(p)), name(h3.findZone(p)));
        }

        Point probe = samples[0];
        System.out.println();
        System.out.println("S2 cell token  (level 12): " + s2.cellToken(probe, 12));
        System.out.println("H3 cell address (res " + H3_RESOLUTION + "): " + h3.cellAddress(probe));
        System.out.println("H3 zones within 2 rings of (77.595,13.015): "
                + h3.zonesNear(new Point(77.595, 13.015), 2));

        System.out.println();
        System.out.println("-- 200k random points: agreement with exact ray casting, and speed --");
        compare(exact, s2, h3);
    }

    private static void compare(GeofenceService exact, S2GeofenceIndex s2, H3GeofenceIndex h3) {
        Random rnd = new Random(1);
        int n = 200_000;
        Point[] pts = new Point[n];
        for (int i = 0; i < n; i++) {
            pts[i] = new Point(77.45 + rnd.nextDouble() * 0.45, 12.85 + rnd.nextDouble() * 0.30);
        }
        String[] truth = new String[n];
        String[] s2Out = new String[n];
        String[] h3Out = new String[n];

        for (int i = 0; i < 20_000; i++) { // JIT warm-up
            exact.findZone(pts[i]);
            s2.findZone(pts[i]);
            h3.findZone(pts[i]);
        }

        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) truth[i] = exact.findZone(pts[i]).orElse(null);
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) s2Out[i] = s2.findZone(pts[i]).orElse(null);
        long t2 = System.nanoTime();
        for (int i = 0; i < n; i++) h3Out[i] = h3.findZone(pts[i]).orElse(null);
        long t3 = System.nanoTime();

        System.out.printf("Ray casting + bbox : %4d ms%n", (t1 - t0) / 1_000_000);
        System.out.printf("S2 index           : %4d ms, agreement %.3f%%  (exact tests: %d, interior hits: %d)%n",
                (t2 - t1) / 1_000_000, agreement(truth, s2Out), s2.exactChecks(), s2.interiorHits());
        System.out.printf("H3 index (res %d)   : %4d ms, agreement %.3f%%  (approximate near borders)%n",
                H3_RESOLUTION, (t3 - t2) / 1_000_000, agreement(truth, h3Out));
    }

    static double agreement(String[] expected, String[] actual) {
        int same = 0;
        for (int i = 0; i < expected.length; i++) {
            if (java.util.Objects.equals(expected[i], actual[i])) same++;
        }
        return 100.0 * same / expected.length;
    }

    private static String name(Optional<String> zone) {
        return zone.orElse("none");
    }
}
