package com.example.raycasting;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PostGisGeoToolsTest {

    private static GeofenceService exact;
    private static GeoToolsGeofenceIndex geoTools;
    private static PostGisGeofenceIndex postGis;

    @BeforeAll
    static void setUp() {
        exact = CellIndexDemo.baseline();
        geoTools = new GeoToolsGeofenceIndex();
        CellIndexDemo.register(geoTools);
        if (PostGisConfig.isAvailable()) {
            postGis = new PostGisGeofenceIndex("junit_zones");
            CellIndexDemo.register(postGis);
        }
    }

    @AfterAll
    static void tearDown() {
        if (postGis != null) {
            postGis.close();
        }
    }

    // ---- GeoTools (in-memory, always runs) ----

    @Test
    void geoToolsMatchesRayCastingOnConcaveZone() {
        Point[] points = {
                new Point(77.55, 12.94), new Point(77.65, 13.02), new Point(77.54, 13.03),
                new Point(77.78, 12.97), new Point(77.40, 12.50)
        };
        for (Point p : points) {
            assertEquals(exact.findZone(p), geoTools.findZone(p), "mismatch at " + p);
        }
    }

    @Test
    void geoToolsAgreesWithRayCastingOnRandomPoints() {
        Random rnd = new Random(11);
        for (int i = 0; i < 5000; i++) {
            Point p = new Point(77.45 + rnd.nextDouble() * 0.45, 12.85 + rnd.nextDouble() * 0.30);
            assertEquals(exact.findZone(p), geoTools.findZone(p), "mismatch at " + p);
        }
    }

    @Test
    void geoToolsMetricDistanceIsInMetres() {
        // 0.1 degrees of latitude is about 11.1 km
        Point south = new Point(77.55, 12.80); // zone L's bottom edge is at lat 12.90
        double metres = geoTools.distanceToZoneMeters(south, "Zone-L");
        assertTrue(metres > 11_000 && metres < 11_300, "unexpected distance: " + metres);
    }

    // ---- PostGIS (needs the container, skipped otherwise) ----

    @Test
    void postGisMatchesRayCastingOnConcaveZone() {
        assumeTrue(postGis != null, "PostGIS container not running");
        Point[] points = {
                new Point(77.55, 12.94), new Point(77.65, 13.02), new Point(77.54, 13.03),
                new Point(77.78, 12.97), new Point(77.40, 12.50)
        };
        for (Point p : points) {
            assertEquals(exact.findZone(p), postGis.findZone(p), "mismatch at " + p);
        }
    }

    @Test
    void postGisAgreesWithRayCastingOnRandomPoints() {
        assumeTrue(postGis != null, "PostGIS container not running");
        Random rnd = new Random(12);
        for (int i = 0; i < 500; i++) {
            Point p = new Point(77.45 + rnd.nextDouble() * 0.45, 12.85 + rnd.nextDouble() * 0.30);
            assertEquals(exact.findZone(p), postGis.findZone(p), "mismatch at " + p);
        }
    }

    @Test
    void postGisAndGeoToolsDistancesAgreeWithinAFewMetres() {
        assumeTrue(postGis != null, "PostGIS container not running");
        Point outside = new Point(77.5, 12.7);
        double geography = postGis.distanceToZoneMeters(outside, "Zone-L");
        double utm = geoTools.distanceToZoneMeters(outside, "Zone-L");
        // geography is on the spheroid, UTM has a small scale distortion
        assertEquals(geography, utm, geography * 0.002);
    }

    @Test
    void postGisInsideZoneHasZeroDistance() {
        assumeTrue(postGis != null, "PostGIS container not running");
        assertEquals(0.0, postGis.distanceToZoneMeters(new Point(77.78, 12.97), "Zone-East"), 1e-9);
    }

    @Test
    void postGisFindsZonesWithinRadiusAndNearest() {
        assumeTrue(postGis != null, "PostGIS container not running");
        assertEquals(List.of("Zone-East", "Zone-L"), postGis.zonesWithinMeters(new Point(77.71, 12.97), 5000));
        assertEquals("Zone-NorthWest", postGis.nearestZones(new Point(77.65, 13.20), 2).get(0));
    }

    @Test
    void postGisUsesGistIndexOnLargeTable() {
        assumeTrue(postGis != null, "PostGIS container not running");
        try (PostGisGeofenceIndex big = new PostGisGeofenceIndex("junit_scale_zones")) {
            big.generateGridZones(50, 50, 77.0, 12.0, 0.01);
            assertEquals(2500, big.zoneCount());
            String plan = String.join("\n", big.explain(new Point(77.255, 12.255)));
            assertTrue(plan.contains("Index") && plan.contains("junit_scale_zones_geom_gix"),
                    "expected a GiST index scan but got:\n" + plan);
            assertEquals("cell-25-25", big.findZone(new Point(77.255, 12.255)).orElseThrow());
        }
    }

    @Test
    void geoToolsOverPostGisTranslatesFiltersToQueries() {
        assumeTrue(postGis != null, "PostGIS container not running");
        try (GeoToolsPostGisSource source = new GeoToolsPostGisSource(postGis.tableName())) {
            assertEquals(3, source.count());
            assertEquals(List.of("Zone-East"), source.zonesContaining(new Point(77.78, 12.97)));
            assertEquals(List.of("Zone-NorthWest"),
                    source.zonesInBoxWithNameLike(77.0, 12.5, 78.0, 13.5, "Zone-N%"));
            assertTrue(source.zonesContaining(new Point(77.40, 12.50)).isEmpty());
        }
    }

    @Test
    void rejectsUnsafeTableName() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PostGisGeofenceIndex("zones; DROP TABLE users"));
    }
}
