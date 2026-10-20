package com.example.raycasting;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Concave "L" shaped polygon: ray casting works on concave shapes too.
        List<Point> lShape = List.of(
                new Point(0, 0), new Point(6, 0), new Point(6, 2),
                new Point(2, 2), new Point(2, 6), new Point(0, 6));

        RayCastingPolygon own = new RayCastingPolygon(lShape);
        JtsPointInPolygon jts = new JtsPointInPolygon(lShape);
        AwtPointInPolygon awt = new AwtPointInPolygon(lShape);

        System.out.println("=== 1. Same polygon, three implementations ===");
        System.out.printf("%-14s %-8s %-8s %-8s %s%n", "Point", "Own", "JTS", "AWT", "JTS location");
        Point[] points = {
                new Point(1, 1),   // inside the vertical arm
                new Point(4, 1),   // inside the horizontal arm
                new Point(4, 4),   // in the concave notch -> outside
                new Point(7, 1),   // outside
                new Point(-1, 3),  // outside
                new Point(2, 4)    // on the boundary
        };
        for (Point p : points) {
            System.out.printf("%-14s %-8s %-8s %-8s %s%n",
                    "(" + p.x() + "," + p.y() + ")",
                    own.contains(p), jts.contains(p), awt.contains(p), jts.locate(p));
        }

        System.out.println();
        System.out.println("=== 2. Geofencing with bounding-box pre-filter ===");
        GeofenceService service = new GeofenceService();
        service.addZone("Zone-A", square(0, 0, 10));
        service.addZone("Zone-B", square(20, 0, 10));
        service.addZone("Zone-C", square(40, 0, 10));

        Point[] pings = {new Point(5, 5), new Point(25, 5), new Point(45, 5), new Point(100, 100)};
        for (Point p : pings) {
            System.out.printf("(%s,%s) -> %s%n", p.x(), p.y(), service.findZone(p).orElse("no zone"));
        }
        System.out.println("Ray cast calls: " + service.rayCastCalls()
                + " (naive approach would need " + (pings.length * 3) + " at most)");

        System.out.println();
        System.out.println("=== 3. Rough benchmark (1M points, 1000-vertex polygon) ===");
        benchmark();
        System.out.println();
        System.out.println("=== 4. Cell indexes: Google S2 and Uber H3 ===");
        CellIndexDemo.run();

        System.out.println();
        System.out.println("=== 5. PostGIS and GeoTools ===");
        PostGisGeoToolsDemo.run();
    }

    private static RayCastingPolygon square(double x, double y, double size) {
        return new RayCastingPolygon(List.of(
                new Point(x, y), new Point(x + size, y),
                new Point(x + size, y + size), new Point(x, y + size)));
    }

    private static void benchmark() {
        int n = 1000;
        List<Point> circle = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            circle.add(new Point(100 * Math.cos(a), 100 * Math.sin(a)));
        }
        RayCastingPolygon own = new RayCastingPolygon(circle);
        JtsPointInPolygon jts = new JtsPointInPolygon(circle);

        java.util.Random rnd = new java.util.Random(42);
        int queries = 1_000_000;
        Point[] pts = new Point[queries];
        for (int i = 0; i < queries; i++) {
            pts[i] = new Point(rnd.nextDouble() * 240 - 120, rnd.nextDouble() * 240 - 120);
        }

        // warm-up so the JIT does not distort the first measurement
        for (int i = 0; i < 50_000; i++) {
            own.contains(pts[i]);
            jts.contains(pts[i]);
        }

        long t0 = System.nanoTime();
        int ownInside = 0;
        for (Point p : pts) if (own.contains(p)) ownInside++;
        long t1 = System.nanoTime();
        int jtsInside = 0;
        for (Point p : pts) if (jts.contains(p)) jtsInside++;
        long t2 = System.nanoTime();

        System.out.printf("Own ray casting (O(n))      : %5d ms, inside=%d%n", (t1 - t0) / 1_000_000, ownInside);
        System.out.printf("JTS PreparedGeometry (index): %5d ms, inside=%d%n", (t2 - t1) / 1_000_000, jtsInside);
    }
}
