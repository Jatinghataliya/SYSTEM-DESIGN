package com.example.quadtree;

import java.util.List;
import java.util.Random;

/**
 * Demo: builds a Quadtree from random 2-D points and runs range / nearest queries.
 *
 * <p>Run with: {@code mvn compile exec:java}
 */
public class Main {

    public static void main(String[] args) {

        // ------------------------------------------------------------------ //
        // 1. Build the tree
        // ------------------------------------------------------------------ //
        // Coordinate system: x = longitude [-180, 180], y = latitude [-90, 90]
        QuadTree tree = new QuadTree(0, 0, 180, 90, 4);

        Random rnd = new Random(42);
        int total = 1_000;
        for (int i = 0; i < total; i++) {
            double lon = rnd.nextDouble() * 360 - 180;
            double lat = rnd.nextDouble() * 180 - 90;
            tree.insert(new Point(lon, lat, "City-" + i));
        }

        // Add a few well-known cities for deterministic lookup
        tree.insert(new Point(2.3522,  48.8566, "Paris"));
        tree.insert(new Point(-74.006, 40.7128, "New York"));
        tree.insert(new Point(139.691, 35.6895, "Tokyo"));
        tree.insert(new Point(-0.1276, 51.5074, "London"));
        tree.insert(new Point(77.2090, 28.6139, "Delhi"));

        System.out.println("=== Quadtree Demo ===");
        System.out.printf("Inserted %d points%n", tree.size());
        System.out.printf("Tree nodes : %d%n", tree.nodeCount());
        System.out.printf("Max depth  : %d%n%n", tree.maxDepth());

        // ------------------------------------------------------------------ //
        // 2. Range query: bounding box around Western Europe
        // ------------------------------------------------------------------ //
        // Approx: lon [-10, 20], lat [35, 55]
        BoundingBox europe = new BoundingBox(5, 45, 15, 10);
        long t0 = System.nanoTime();
        List<Point> inEurope = tree.query(europe);
        long t1 = System.nanoTime();

        System.out.printf("--- Range query: Western Europe box %s ---%n", europe);
        System.out.printf("Found %d points in %.3f ms%n", inEurope.size(), (t1 - t0) / 1e6);
        inEurope.stream()
                .filter(p -> !p.label.startsWith("City-"))
                .forEach(p -> System.out.printf("  Named city: %s%n", p));
        System.out.println();

        // ------------------------------------------------------------------ //
        // 3. Range query: tight box around Manhattan
        // ------------------------------------------------------------------ //
        // lon [-74.1, -73.9], lat [40.6, 40.8]
        BoundingBox manhattan = new BoundingBox(-74.0, 40.7, 0.1, 0.1);
        List<Point> nearNY = tree.query(manhattan);
        System.out.printf("--- Range query: Manhattan box %s ---%n", manhattan);
        System.out.printf("Found %d point(s)%n", nearNY.size());
        nearNY.forEach(p -> System.out.printf("  %s%n", p));
        System.out.println();

        // ------------------------------------------------------------------ //
        // 4. Nearest-neighbour queries
        // ------------------------------------------------------------------ //
        System.out.println("--- Nearest-neighbour queries ---");
        double[][] queries = {
            {2.3522, 48.8566},   // Paris
            {139.691, 35.6895},  // Tokyo
            {0, 0},              // Gulf of Guinea (no named city)
        };
        String[] queryLabels = {"Paris (2.35, 48.86)", "Tokyo (139.69, 35.69)", "Null Island (0, 0)"};

        for (int i = 0; i < queries.length; i++) {
            double qx = queries[i][0], qy = queries[i][1];
            long ts = System.nanoTime();
            Point nearest = tree.findNearest(qx, qy);
            long te = System.nanoTime();
            System.out.printf("Query %-30s -> nearest: %-20s  dist=%.4f  time=%.3f ms%n",
                    queryLabels[i],
                    nearest != null ? nearest.label : "null",
                    nearest != null ? euclidean(qx, qy, nearest.x, nearest.y) : Double.NaN,
                    (te - ts) / 1e6);
        }
        System.out.println();

        // ------------------------------------------------------------------ //
        // 5. Visualise the density split (top-level 4 quadrants)
        // ------------------------------------------------------------------ //
        System.out.println("--- Quadrant point counts (manual, top-level split at 0,0) ---");
        BoundingBox nw = new BoundingBox(-90, 45, 90, 45);
        BoundingBox ne = new BoundingBox( 90, 45, 90, 45);
        BoundingBox sw = new BoundingBox(-90,-45, 90, 45);
        BoundingBox se = new BoundingBox( 90,-45, 90, 45);
        System.out.printf("  NW (Americas/Europe): %d%n", tree.query(nw).size());
        System.out.printf("  NE (Asia/Pacific):    %d%n", tree.query(ne).size());
        System.out.printf("  SW (S. America/W.Afr):%d%n", tree.query(sw).size());
        System.out.printf("  SE (Africa/Australia):%d%n", tree.query(se).size());
    }

    private static double euclidean(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
