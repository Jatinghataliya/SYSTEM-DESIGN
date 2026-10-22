package com.example.quadtreejts;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.List;
import java.util.Random;

/**
 * Demo: JTS {@link Quadtree} for 2-D spatial indexing.
 *
 * <p>JTS Quadtree indexes objects by their {@link Envelope} (bounding box).
 * Each item is inserted together with the envelope of its location, then
 * queried by passing a search envelope.  Items whose envelopes intersect
 * the search envelope are returned as candidates; an exact check is done
 * afterwards because JTS may return false positives.
 *
 * <p>Run: {@code mvn compile exec:java}
 */
public class Main {

    // Known cities
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

    public static void main(String[] args) {

        // ------------------------------------------------------------------ //
        // 1. Build the JTS Quadtree
        // ------------------------------------------------------------------ //
        Quadtree index = new Quadtree();

        // Insert known cities
        for (Place p : CITIES) {
            // A point-envelope: min == max for both x and y
            Envelope env = new Envelope(new Coordinate(p.lon(), p.lat()));
            index.insert(env, p);
        }

        // Insert 500 random points
        Random rnd = new Random(42);
        for (int i = 0; i < 500; i++) {
            double lon = rnd.nextDouble() * 360 - 180;
            double lat = rnd.nextDouble() * 180 - 90;
            Place rp = new Place("Rnd-" + i, lon, lat);
            index.insert(new Envelope(new Coordinate(lon, lat)), rp);
        }

        System.out.println("=== JTS Quadtree Demo ===");
        System.out.printf("Total items in index : %d%n", index.size());
        System.out.println();

        // ------------------------------------------------------------------ //
        // 2. Range query – bounding box around Europe
        // ------------------------------------------------------------------ //
        // lon [-10, 30], lat [35, 60]
        Envelope europe = new Envelope(-10, 30, 35, 60);
        List<?> europeHits = index.query(europe);
        // JTS may return false positives – filter exactly
        long europeCount = europeHits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return europe.contains(new Coordinate(p.lon(), p.lat()));
            })
            .count();

        System.out.printf("--- Range query: Europe %s ---%n", europe);
        System.out.printf("Candidates : %d  |  Exact matches : %d%n",
                europeHits.size(), europeCount);
        europeHits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return europe.contains(new Coordinate(p.lon(), p.lat()))
                    && !((Place) o).name().startsWith("Rnd");
            })
            .forEach(o -> System.out.printf("  Named: %s%n", o));
        System.out.println();

        // ------------------------------------------------------------------ //
        // 3. Range query – tight box around Tokyo
        // ------------------------------------------------------------------ //
        Envelope tokyoBox = new Envelope(138.0, 141.0, 34.0, 37.0);
        List<?> tokyoHits = index.query(tokyoBox);
        System.out.printf("--- Range query: Tokyo box %s ---%n", tokyoBox);
        tokyoHits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return tokyoBox.contains(new Coordinate(p.lon(), p.lat()));
            })
            .forEach(o -> System.out.printf("  %s%n", o));
        System.out.println();

        // ------------------------------------------------------------------ //
        // 4. Remove an item and re-query
        // ------------------------------------------------------------------ //
        Place berlin = CITIES[6]; // Berlin
        boolean removed = index.remove(
            new Envelope(new Coordinate(berlin.lon(), berlin.lat())), berlin);
        System.out.printf("--- Remove Berlin: %s  |  size after: %d ---%n%n",
                removed, index.size());

        // ------------------------------------------------------------------ //
        // 5. Envelope expansion (simulate radius search ~500 km at equator)
        // ------------------------------------------------------------------ //
        double queryLon = 0.0, queryLat = 0.0; // Null Island
        double degRadius = 4.5; // ≈ 500 km
        Envelope radEnv = new Envelope(
            queryLon - degRadius, queryLon + degRadius,
            queryLat - degRadius, queryLat + degRadius);
        List<?> radHits = index.query(radEnv);
        long radExact = radHits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return radEnv.contains(new Coordinate(p.lon(), p.lat()));
            })
            .count();
        System.out.printf("--- Radius search ~500 km around Null Island ---%n");
        System.out.printf("Candidates : %d  |  Exact : %d%n%n", radHits.size(), radExact);

        // ------------------------------------------------------------------ //
        // 6. JTS key concepts recap
        // ------------------------------------------------------------------ //
        System.out.println("=== JTS Quadtree Key Concepts ===");
        System.out.println("  insert(Envelope, Object) – index an item by its bounding box");
        System.out.println("  query(Envelope)          – returns candidates (may include false positives)");
        System.out.println("  remove(Envelope, Object) – remove an item by envelope + reference");
        System.out.println("  size()                   – total number of items");
        System.out.println("  Always post-filter: JTS returns envelope-intersecting candidates,");
        System.out.println("  not guaranteed-inside items.");
    }
}
