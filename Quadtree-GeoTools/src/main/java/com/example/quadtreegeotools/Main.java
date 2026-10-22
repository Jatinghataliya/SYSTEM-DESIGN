package com.example.quadtreegeotools;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.List;
import java.util.Random;

/**
 * Demo: GeoTools {@link ReferencedEnvelope} + JTS {@link Quadtree}.
 *
 * <p>GeoTools builds on JTS.  Its unique value is the {@link ReferencedEnvelope}:
 * a bounding box bound to a {@link CoordinateReferenceSystem} (CRS).  When you
 * transform an envelope from one CRS to another, GeoTools reprojects the corners
 * correctly.  The spatial index is still the JTS Quadtree under the hood, but
 * all coordinates are CRS-aware.
 *
 * <p>Note: GeoTools 30+ moved OGC interfaces from {@code org.opengis.*}
 * to {@code org.geotools.api.*}.
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
        // 1. Obtain the WGS 84 CRS (EPSG:4326)
        // ------------------------------------------------------------------ //
        // GeoTools 30+: CRS is in org.geotools.referencing.CRS
        // The returned type is org.geotools.api.referencing.crs.CoordinateReferenceSystem
        CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true); // lon/lat axis order
        System.out.println("=== GeoTools Quadtree Demo ===");
        System.out.println("CRS: " + wgs84.getName());
        System.out.println();

        // ------------------------------------------------------------------ //
        // 2. Build a JTS Quadtree
        // ------------------------------------------------------------------ //
        Quadtree index = new Quadtree();

        for (Place p : CITIES) {
            Envelope env = new Envelope(new Coordinate(p.lon(), p.lat()));
            index.insert(env, p);
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
        // 3. ReferencedEnvelope for Europe – CRS-aware bounding box
        // ------------------------------------------------------------------ //
        // ReferencedEnvelope(minLon, maxLon, minLat, maxLat, crs)
        ReferencedEnvelope europeEnv = new ReferencedEnvelope(-10, 30, 35, 60, wgs84);
        System.out.printf("--- CRS-aware range query: Europe %s ---%n", europeEnv);
        System.out.printf("    CRS name: %s%n", europeEnv.getCoordinateReferenceSystem().getName());

        List<?> europeHits = index.query(new Envelope(
            europeEnv.getMinX(), europeEnv.getMaxX(),
            europeEnv.getMinY(), europeEnv.getMaxY()));

        europeHits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return europeEnv.contains(new Coordinate(p.lon(), p.lat()))
                    && !p.name().startsWith("Rnd");
            })
            .forEach(o -> System.out.printf("  Named: %s%n", o));
        System.out.println();

        // ------------------------------------------------------------------ //
        // 4. Project the Europe bounding box to Web Mercator (EPSG:3857)
        // ------------------------------------------------------------------ //
        CoordinateReferenceSystem webMercator = CRS.decode("EPSG:3857");
        ReferencedEnvelope mercatorEnv = europeEnv.transform(webMercator, true);
        System.out.println("--- Europe envelope reprojected to EPSG:3857 (Web Mercator) ---");
        System.out.printf("  minX=%.1f  maxX=%.1f  minY=%.1f  maxY=%.1f  (metres)%n%n",
            mercatorEnv.getMinX(), mercatorEnv.getMaxX(),
            mercatorEnv.getMinY(), mercatorEnv.getMaxY());

        // ------------------------------------------------------------------ //
        // 5. Tight box around Tokyo
        // ------------------------------------------------------------------ //
        ReferencedEnvelope tokyoEnv = new ReferencedEnvelope(138.0, 141.0, 34.0, 37.0, wgs84);
        List<?> tokyoHits = index.query(new Envelope(
            tokyoEnv.getMinX(), tokyoEnv.getMaxX(),
            tokyoEnv.getMinY(), tokyoEnv.getMaxY()));
        System.out.printf("--- Range query: Tokyo box ---%n");
        tokyoHits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return tokyoEnv.contains(new Coordinate(p.lon(), p.lat()));
            })
            .forEach(o -> System.out.printf("  %s%n", o));
        System.out.println();

        // ------------------------------------------------------------------ //
        // 6. GeoTools key concepts recap
        // ------------------------------------------------------------------ //
        System.out.println("=== GeoTools Key Concepts ===");
        System.out.println("  ReferencedEnvelope              – bounding box bound to a CRS");
        System.out.println("  CRS.decode(\"EPSG:4326\", true)   – obtain a CoordinateReferenceSystem (lon/lat)");
        System.out.println("  envelope.transform(crs, true)   – reproject envelope to another CRS");
        System.out.println("  JTS Quadtree (via jts-core)     – underlying spatial index");
        System.out.println("  GeoTools 30+ uses org.geotools.api.* instead of org.opengis.*");
    }
}
