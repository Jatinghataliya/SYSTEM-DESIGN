package com.example.raycasting;

import java.util.List;
import java.util.Random;

/**
 * Demo for PostGIS and GeoTools, using the same three Bengaluru-like zones as {@link CellIndexDemo}.
 * Needs the PostGIS container: {@code docker compose up -d}.
 */
public final class PostGisGeoToolsDemo {

    private PostGisGeoToolsDemo() {
    }

    public static void run() {
        if (!PostGisConfig.isAvailable()) {
            System.out.println("PostGIS is not reachable at " + PostGisConfig.JDBC_URL);
            System.out.println("Start it with `docker compose up -d`, then run again. Skipping this section.");
            return;
        }

        GeofenceService exact = CellIndexDemo.baseline();
        GeoToolsGeofenceIndex geoTools = new GeoToolsGeofenceIndex();
        CellIndexDemo.register(geoTools);

        try (PostGisGeofenceIndex postGis = new PostGisGeofenceIndex("demo_zones")) {
            CellIndexDemo.register(postGis);

            System.out.println("-- Sample lookups --");
            System.out.printf("%-18s %-16s %-16s %-16s%n", "Point (lng,lat)", "Ray casting", "PostGIS", "GeoTools");
            Point[] samples = {
                    new Point(77.55, 12.94), new Point(77.65, 13.02), new Point(77.54, 13.03),
                    new Point(77.78, 12.97), new Point(77.40, 12.50)
            };
            for (Point p : samples) {
                System.out.printf("%-18s %-16s %-16s %-16s%n",
                        "(" + p.x() + "," + p.y() + ")",
                        exact.findZone(p).orElse("none"),
                        postGis.findZone(p).orElse("none"),
                        geoTools.findZone(p).orElse("none"));
            }

            System.out.println();
            System.out.println("-- Distances (the part ray casting cannot do) --");
            Point outside = new Point(77.5, 12.7); // south of Zone-L's bottom edge (lat 12.90)
            double pg = postGis.distanceToZoneMeters(outside, "Zone-L");
            double gt = geoTools.distanceToZoneMeters(outside, "Zone-L");
            System.out.printf("Distance from (%.2f,%.2f) to Zone-L: PostGIS geography %.1f m, GeoTools UTM %.1f m%n",
                    outside.x(), outside.y(), pg, gt);
            System.out.println("Zones within 5 km of (77.71, 12.97): "
                    + postGis.zonesWithinMeters(new Point(77.71, 12.97), 5000));
            System.out.println("Nearest 2 zones to (77.65, 13.20): "
                    + postGis.nearestZones(new Point(77.65, 13.20), 2));

            System.out.println();
            System.out.println("-- GeoTools over the PostGIS table (filter becomes SQL) --");
            try (GeoToolsPostGisSource source = new GeoToolsPostGisSource("demo_zones")) {
                System.out.println("Rows seen through GeoTools: " + source.count());
                System.out.println("CONTAINS(point 77.78,12.97): " + source.zonesContaining(new Point(77.78, 12.97)));
                System.out.println("BBOX + name LIKE 'Zone-N%':  "
                        + source.zonesInBoxWithNameLike(77.0, 12.5, 78.0, 13.5, "Zone-N%"));
            }
        }

        System.out.println();
        System.out.println("-- Scale: 10,000 zones, 2,000 random points, with and without the GiST index --");
        scaleTest();
    }

    private static void scaleTest() {
        int side = 100; // 100 x 100 = 10,000 zones
        double size = 0.01;
        double originLng = 77.0, originLat = 12.0;

        // Same grid in memory for the ray casting and GeoTools sides.
        GeofenceService rayOnly = new GeofenceService();
        GeoToolsGeofenceIndex geoTools = new GeoToolsGeofenceIndex();
        for (int r = 0; r < side; r++) {
            for (int c = 0; c < side; c++) {
                String name = "cell-" + r + "-" + c;
                double x = originLng + c * size, y = originLat + r * size;
                List<Point> square = List.of(new Point(x, y), new Point(x + size, y),
                        new Point(x + size, y + size), new Point(x, y + size));
                rayOnly.addZone(name, new RayCastingPolygon(square));
                geoTools.addZone(name, square);
            }
        }

        Random rnd = new Random(5);
        int n = 2000;
        Point[] pts = new Point[n];
        for (int i = 0; i < n; i++) {
            // keep points off exact cell borders so every method agrees
            pts[i] = new Point(originLng + rnd.nextDouble() * side * size, originLat + rnd.nextDouble() * side * size);
        }

        try (PostGisGeofenceIndex postGis = new PostGisGeofenceIndex("scale_zones")) {
            postGis.generateGridZones(side, side, originLng, originLat, size);
            System.out.println("Zones loaded: " + postGis.zoneCount());
            System.out.println("Plan with GiST index:");
            postGis.explain(pts[0]).forEach(line -> System.out.println("    " + line));

            warmUp(rayOnly, geoTools, postGis, pts);

            long t0 = System.nanoTime();
            int rayHits = 0;
            for (Point p : pts) if (rayOnly.findZone(p).isPresent()) rayHits++;
            long t1 = System.nanoTime();
            int gtHits = 0;
            for (Point p : pts) if (geoTools.findZone(p).isPresent()) gtHits++;
            long t2 = System.nanoTime();
            int pgHits = 0;
            for (Point p : pts) if (postGis.findZone(p).isPresent()) pgHits++;
            long t3 = System.nanoTime();

            postGis.dropSpatialIndex();
            System.out.println("Plan without index:");
            postGis.explain(pts[0]).forEach(line -> System.out.println("    " + line));
            int seqHits = 0;
            long t4 = System.nanoTime();
            for (int i = 0; i < 200; i++) if (postGis.findZone(pts[i]).isPresent()) seqHits++; // only 200: it is slow
            long t5 = System.nanoTime();
            postGis.createSpatialIndex();

            System.out.printf("Ray casting + bbox loop over 10k zones : %6.3f ms/query (hits=%d)%n",
                    (t1 - t0) / 1e6 / n, rayHits);
            System.out.printf("GeoTools STRtree + CONTAINS filter     : %6.3f ms/query (hits=%d)%n",
                    (t2 - t1) / 1e6 / n, gtHits);
            System.out.printf("PostGIS with GiST index (JDBC)         : %6.3f ms/query (hits=%d)%n",
                    (t3 - t2) / 1e6 / n, pgHits);
            System.out.printf("PostGIS without index (sequential scan): %6.3f ms/query (hits=%d of 200)%n",
                    (t5 - t4) / 1e6 / 200, seqHits);
        }
    }

    private static void warmUp(GeofenceService rayOnly, GeoToolsGeofenceIndex geoTools,
                               PostGisGeofenceIndex postGis, Point[] pts) {
        for (int i = 0; i < 200; i++) {
            rayOnly.findZone(pts[i]);
            geoTools.findZone(pts[i]);
            postGis.findZone(pts[i]);
        }
    }
}
