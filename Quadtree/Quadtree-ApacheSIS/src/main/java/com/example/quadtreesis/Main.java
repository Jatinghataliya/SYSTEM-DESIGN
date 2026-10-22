package com.example.quadtreesis;

import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.geometry.Envelope2D;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.CommonCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

import java.util.List;
import java.util.Random;

/**
 * Demo: Apache SIS geodetic calculations + JTS Quadtree spatial index.
 *
 * <p>Apache SIS does not expose a public Quadtree API.  What SIS contributes
 * is precise geodetic arithmetic: CRS definitions, coordinate transformations,
 * and geodesic distance calculations.  This demo shows the natural pairing:
 *
 * <ul>
 *   <li>JTS Quadtree – fast 2-D candidate retrieval</li>
 *   <li>Apache SIS   – accurate geodesic distance filtering</li>
 * </ul>
 *
 * <p>Run: {@code mvn compile exec:java}
 */
public class Main {

    private static final Place[] CITIES = {
        new Place("Paris",     2.3522,  48.8566),
        new Place("London",   -0.1276,  51.5074),
        new Place("Tokyo",   139.6917,  35.6895),
        new Place("New York", -74.0060, 40.7128),
        new Place("Delhi",    77.2090,  28.6139),
        new Place("Sydney",  151.2093, -33.8688),
        new Place("Berlin",   13.4050,  52.5200),
        new Place("Cairo",    31.2357,  30.0444),
        new Place("Rio",     -43.1729, -22.9068),
        new Place("Lagos",     3.3792,   6.5244),
    };

    public static void main(String[] args) throws Exception {

        // ------------------------------------------------------------------ //
        // 1. Apache SIS CRS setup
        // ------------------------------------------------------------------ //
        // normalizedGeographic() guarantees (longitude, latitude) axis order
        CoordinateReferenceSystem wgs84   = CommonCRS.WGS84.normalizedGeographic();
        CoordinateReferenceSystem spherical = CommonCRS.SPHERE.normalizedGeographic();

        System.out.println("=== Apache SIS + JTS Quadtree Demo ===");
        System.out.println("WGS84  CRS: " + wgs84.getName());
        System.out.println("Sphere CRS: " + spherical.getName());
        System.out.println();

        // ------------------------------------------------------------------ //
        // 2. Build the JTS Quadtree
        // ------------------------------------------------------------------ //
        Quadtree index = new Quadtree();
        for (Place p : CITIES) {
            index.insert(new Envelope(new Coordinate(p.lon(), p.lat())), p);
        }

        Random rnd = new Random(42);
        for (int i = 0; i < 500; i++) {
            double lon = rnd.nextDouble() * 360 - 180;
            double lat = rnd.nextDouble() * 180 - 90;
            Place rp = new Place("Rnd-" + i, lon, lat);
            index.insert(new Envelope(new Coordinate(lon, lat)), rp);
        }
        System.out.printf("Total items in index : %d%n%n", index.size());

        // ------------------------------------------------------------------ //
        // 3. Geodetic distance: Paris → London using Apache SIS
        // ------------------------------------------------------------------ //
        System.out.println("--- Apache SIS geodetic distances ---");
        printGeodesicDistance(wgs84, "Paris",    2.3522,  48.8566, "London",  -0.1276, 51.5074);
        printGeodesicDistance(wgs84, "Paris",    2.3522,  48.8566, "New York",-74.0060, 40.7128);
        printGeodesicDistance(wgs84, "Tokyo",  139.6917,  35.6895, "Sydney",  151.2093,-33.8688);
        System.out.println();

        // ------------------------------------------------------------------ //
        // 4. CRS transform: WGS84 lon/lat → Web Mercator (EPSG:3857)
        // ------------------------------------------------------------------ //
        // UTM Zone 30N is available without the full EPSG DB installation
        CoordinateReferenceSystem utm30n = CommonCRS.WGS84.universal(48.85, 2.35); // lat, lon
        MathTransform toUtm = CRS.findOperation(wgs84, utm30n, null).getMathTransform();

        System.out.println("--- WGS84 → UTM Zone 30N projection (Paris) ---");
        DirectPosition parisWgs84 = new DirectPosition2D(wgs84, 2.3522, 48.8566);
        DirectPosition parisUtm = toUtm.transform(parisWgs84, null);
        System.out.printf("  WGS84 : lon=%.4f  lat=%.4f%n",
            parisWgs84.getOrdinate(0), parisWgs84.getOrdinate(1));
        System.out.printf("  UTM30N: easting=%.1f m  northing=%.1f m%n%n",
            parisUtm.getOrdinate(0), parisUtm.getOrdinate(1));

        // ------------------------------------------------------------------ //
        // 5. Range query with JTS index + SIS geodesic distance post-filter
        //    Find all cities within 3 000 km of Paris
        // ------------------------------------------------------------------ //
        double queryLon = 2.3522, queryLat = 48.8566;
        double radiusKm = 3_000.0;
        // Approximate degree-bounding-box: 3000 km ≈ 27° at equator
        double degPad = radiusKm / 111.32;
        Envelope searchEnv = new Envelope(
            queryLon - degPad, queryLon + degPad,
            queryLat - degPad, queryLat + degPad);

        List<?> candidates = index.query(searchEnv);

        System.out.printf("--- Cities within %.0f km of Paris (SIS geodesic filter) ---%n", radiusKm);
        System.out.printf("JTS candidates: %d%n", candidates.size());

        for (Object obj : candidates) {
            Place p = (Place) obj;
            if (p.name().startsWith("Rnd")) continue; // skip random noise
            double distKm = geodesicDistanceKm(wgs84, queryLon, queryLat, p.lon(), p.lat());
            if (distKm <= radiusKm) {
                System.out.printf("  %-12s  %.0f km%n", p.name(), distKm);
            }
        }
        System.out.println();

        // ------------------------------------------------------------------ //
        // 6. Apache SIS Envelope2D (CRS-aware)
        // ------------------------------------------------------------------ //
        System.out.println("--- Apache SIS Envelope2D (CRS-aware) ---");
        Envelope2D sisEnv = new Envelope2D(wgs84, -10, 35, 40, 25); // x, y, width, height
        System.out.printf("  SIS Envelope : %s%n", sisEnv);
        System.out.printf("  CRS          : %s%n%n", sisEnv.getCoordinateReferenceSystem().getName());

        // ------------------------------------------------------------------ //
        // 7. Key concepts recap
        // ------------------------------------------------------------------ //
        System.out.println("=== Apache SIS Key Concepts ===");
        System.out.println("  CommonCRS.WGS84.geographic()        – standard WGS84 CRS");
        System.out.println("  CRS.forCode(\"EPSG:3857\")            – lookup CRS by EPSG code");
        System.out.println("  CRS.findOperation(src, tgt, area)   – find coordinate transform");
        System.out.println("  MathTransform.transform(pos, dest)  – reproject a position");
        System.out.println("  Envelope2D(crs, x, y, w, h)         – CRS-aware bounding box");
        System.out.println("  SIS provides geodetic precision; JTS Quadtree provides speed.");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static void printGeodesicDistance(
            CoordinateReferenceSystem crs,
            String nameA, double lonA, double latA,
            String nameB, double lonB, double latB) throws Exception {

        double km = geodesicDistanceKm(crs, lonA, latA, lonB, latB);
        System.out.printf("  %s → %s : %.1f km%n", nameA, nameB, km);
    }

    /**
     * Compute geodesic distance in kilometres using Apache SIS.
     * SIS 1.4: setStartPoint / setEndPoint accept a DirectPosition.
     */
    private static double geodesicDistanceKm(
            CoordinateReferenceSystem crs,
            double lonA, double latA,
            double lonB, double latB) throws Exception {

        var calc = org.apache.sis.referencing.GeodeticCalculator.create(crs);
        calc.setStartPoint(new DirectPosition2D(crs, lonA, latA));
        calc.setEndPoint(new DirectPosition2D(crs, lonB, latB));
        return calc.getGeodesicDistance() / 1000.0;
    }
}
