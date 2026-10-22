package com.example.quadtreegeotools;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GeoTools {@link ReferencedEnvelope} + JTS {@link Quadtree}.
 */
class QuadtreeGeoToolsTest {

    private CoordinateReferenceSystem wgs84;

    @BeforeEach
    void setUp() throws Exception {
        wgs84 = CRS.decode("EPSG:4326", true);
    }

    // ------------------------------------------------------------------ //
    // ReferencedEnvelope basics
    // ------------------------------------------------------------------ //

    @Test
    void referencedEnvelope_hasCrs() {
        ReferencedEnvelope env = new ReferencedEnvelope(-10, 30, 35, 60, wgs84);
        assertNotNull(env.getCoordinateReferenceSystem());
    }

    @Test
    void referencedEnvelope_contains_pointInside() {
        ReferencedEnvelope env = new ReferencedEnvelope(-10, 30, 35, 60, wgs84);
        assertTrue(env.contains(new Coordinate(2.35, 48.85)));  // Paris
    }

    @Test
    void referencedEnvelope_doesNotContain_pointOutside() {
        ReferencedEnvelope env = new ReferencedEnvelope(-10, 30, 35, 60, wgs84);
        assertFalse(env.contains(new Coordinate(139.69, 35.69)));  // Tokyo
    }

    @Test
    void referencedEnvelope_transform_webMercator() throws Exception {
        ReferencedEnvelope wgs = new ReferencedEnvelope(-10, 30, 35, 60, wgs84);
        CoordinateReferenceSystem mercator = CRS.decode("EPSG:3857");
        ReferencedEnvelope merc = wgs.transform(mercator, true);
        assertNotNull(merc);
        // Mercator x values for lon -10° and lon 30° should be in metres (millions)
        assertTrue(merc.getMinX() < -1_000_000, "Mercator minX should be negative");
        assertTrue(merc.getMaxX() > 1_000_000,  "Mercator maxX should be positive");
    }

    // ------------------------------------------------------------------ //
    // JTS Quadtree with ReferencedEnvelope query
    // ------------------------------------------------------------------ //

    @Test
    void quadtree_insertAndQuery_europeReturnsParisAndLondon() {
        Quadtree index = buildCityIndex();

        ReferencedEnvelope europe = new ReferencedEnvelope(-10, 30, 35, 60, wgs84);
        List<?> hits = index.query(new Envelope(
            europe.getMinX(), europe.getMaxX(),
            europe.getMinY(), europe.getMaxY()));

        long named = hits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return europe.contains(new Coordinate(p.lon(), p.lat()));
            })
            .filter(o -> !((Place) o).name().startsWith("Rnd"))
            .count();
        assertTrue(named >= 2);
    }

    @Test
    void quadtree_tokyoBox_containsTokyo() {
        Quadtree index = buildCityIndex();
        Envelope tokyoEnv = new Envelope(138, 141, 34, 37);
        List<?> hits = index.query(tokyoEnv);
        boolean found = hits.stream()
            .filter(o -> tokyoEnv.contains(new Coordinate(((Place) o).lon(), ((Place) o).lat())))
            .anyMatch(o -> ((Place) o).name().equals("Tokyo"));
        assertTrue(found);
    }

    @Test
    void quadtree_remove_decreasesSize() {
        Quadtree index = new Quadtree();
        Place paris = new Place("Paris", 2.3522, 48.8566);
        Envelope env = new Envelope(new Coordinate(paris.lon(), paris.lat()));
        index.insert(env, paris);
        assertEquals(1, index.size());
        boolean removed = index.remove(env, paris);
        assertTrue(removed);
        assertEquals(0, index.size());
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    private Quadtree buildCityIndex() {
        Quadtree idx = new Quadtree();
        Place[] cities = {
            new Place("Paris",   2.3522,  48.8566),
            new Place("London", -0.1276,  51.5074),
            new Place("Tokyo",  139.6917, 35.6895),
        };
        for (Place c : cities) {
            idx.insert(new Envelope(new Coordinate(c.lon(), c.lat())), c);
        }
        return idx;
    }
}
