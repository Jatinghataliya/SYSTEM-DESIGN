package com.example.quadtreesis;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.referencing.GeodeticCalculator;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Apache SIS geodetic calculations + JTS Quadtree.
 *
 * <p>Uses {@link CommonCRS#WGS84}{@code .normalizedGeographic()} which guarantees
 * (longitude, latitude) axis order, matching the lon/lat convention used in the demo.
 */
class QuadtreeSisTest {

    // normalizedGeographic() guarantees (longitude, latitude) axis order
    private static final CoordinateReferenceSystem WGS84 =
        CommonCRS.WGS84.normalizedGeographic();

    // ------------------------------------------------------------------ //
    // Geodetic distance tests (Apache SIS)
    // ------------------------------------------------------------------ //

    @Test
    void geodesicDistance_parisToLondon_approx340km() throws Exception {
        double km = geodesicKm(2.3522, 48.8566, -0.1276, 51.5074);
        // Actual is ~341 km; allow ±5 km tolerance
        assertTrue(km > 336 && km < 346,
            "Paris→London should be ~341 km, got " + km);
    }

    @Test
    void geodesicDistance_parisToNewYork_approx5840km() throws Exception {
        double km = geodesicKm(2.3522, 48.8566, -74.006, 40.7128);
        assertTrue(km > 5800 && km < 5900,
            "Paris→New York should be ~5837 km, got " + km);
    }

    @Test
    void geodesicDistance_samePoint_isZero() throws Exception {
        double km = geodesicKm(10.0, 20.0, 10.0, 20.0);
        assertEquals(0.0, km, 0.001);
    }

    @Test
    void geodesicDistance_symmetrical() throws Exception {
        double ab = geodesicKm(2.35, 48.85, 13.40, 52.52);
        double ba = geodesicKm(13.40, 52.52, 2.35, 48.85);
        assertEquals(ab, ba, 0.01, "Distance should be symmetric");
    }

    // ------------------------------------------------------------------ //
    // CRS tests (Apache SIS)
    // ------------------------------------------------------------------ //

    @Test
    void commonCrs_wgs84_isNotNull() {
        assertNotNull(WGS84);
        // normalizedGeographic() name contains "WGS"
        assertTrue(WGS84.getName().toString().contains("WGS"),
            "CRS name should contain WGS, got: " + WGS84.getName());
    }

    @Test
    void crsTransform_wgs84ToUTM_zone30() throws Exception {
        // UTM zone 30N covers Western Europe / UK; available without the full EPSG DB
        CoordinateReferenceSystem utm30 = CommonCRS.WGS84.universal(48.85, 2.35); // lat, lon
        assertNotNull(utm30);

        var op = org.apache.sis.referencing.CRS.findOperation(WGS84, utm30, null);
        assertNotNull(op.getMathTransform());

        // Transform Paris (lon=2.3522, lat=48.8566)
        var parisMerc = op.getMathTransform().transform(
            new DirectPosition2D(WGS84, 2.3522, 48.8566), null);
        // Paris in UTM-30N: easting ~452,000 m, northing ~5,412,000 m  (±5 km tolerance)
        assertEquals(452_000.0, parisMerc.getOrdinate(0), 5_000.0);
        assertEquals(5_412_000.0, parisMerc.getOrdinate(1), 5_000.0);
    }

    // ------------------------------------------------------------------ //
    // Integration: JTS Quadtree + SIS geodetic distance filter
    // ------------------------------------------------------------------ //

    @Test
    void proximitySearch_3000kmFromParis_containsLondonAndBerlin() throws Exception {
        Quadtree index = buildCityIndex();

        double queryLon = 2.3522, queryLat = 48.8566;
        double radiusKm = 3_000.0;
        double degPad = radiusKm / 111.32;

        Envelope bbox = new Envelope(
            queryLon - degPad, queryLon + degPad,
            queryLat - degPad, queryLat + degPad);
        List<?> candidates = index.query(bbox);

        List<String> within = new ArrayList<>();
        for (Object obj : candidates) {
            Place p = (Place) obj;
            double dist = geodesicKm(queryLon, queryLat, p.lon(), p.lat());
            if (dist <= radiusKm) within.add(p.name());
        }

        assertTrue(within.contains("London"), "London should be within 3000 km of Paris");
        assertTrue(within.contains("Berlin"), "Berlin should be within 3000 km of Paris");
        assertFalse(within.contains("Tokyo"),  "Tokyo should NOT be within 3000 km of Paris");
    }

    @Test
    void proximitySearch_500kmFromParis_containsLondonNotBerlin() throws Exception {
        Quadtree index = buildCityIndex();

        double queryLon = 2.3522, queryLat = 48.8566;
        double radiusKm = 500.0;
        double degPad = radiusKm / 111.32;

        Envelope bbox = new Envelope(
            queryLon - degPad, queryLon + degPad,
            queryLat - degPad, queryLat + degPad);
        List<?> candidates = index.query(bbox);

        List<String> within = new ArrayList<>();
        for (Object obj : candidates) {
            Place p = (Place) obj;
            double dist = geodesicKm(queryLon, queryLat, p.lon(), p.lat());
            if (dist <= radiusKm) within.add(p.name());
        }

        assertTrue(within.contains("London"), "London is ~341 km from Paris");
        // Berlin is ~878 km from Paris – should not appear
        assertFalse(within.contains("Berlin"), "Berlin is ~878 km – outside 500 km");
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    private double geodesicKm(double lonA, double latA, double lonB, double latB) throws Exception {
        GeodeticCalculator calc = GeodeticCalculator.create(WGS84);
        calc.setStartPoint(new DirectPosition2D(WGS84, lonA, latA));
        calc.setEndPoint(new DirectPosition2D(WGS84, lonB, latB));
        return calc.getGeodesicDistance() / 1000.0;
    }

    private Quadtree buildCityIndex() {
        Quadtree idx = new Quadtree();
        Place[] cities = {
            new Place("Paris",    2.3522,  48.8566),
            new Place("London",  -0.1276,  51.5074),
            new Place("Tokyo",  139.6917,  35.6895),
            new Place("Berlin",  13.4050,  52.5200),
            new Place("New York",-74.0060, 40.7128),
        };
        for (Place c : cities) {
            idx.insert(new Envelope(new Coordinate(c.lon(), c.lat())), c);
        }
        return idx;
    }
}
