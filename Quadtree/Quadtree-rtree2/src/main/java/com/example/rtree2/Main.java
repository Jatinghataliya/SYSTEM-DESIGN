package com.example.rtree2;

import com.github.davidmoten.rtree2.Entry;
import com.github.davidmoten.rtree2.RTree;
import com.github.davidmoten.rtree2.geometry.Geometries;
import com.github.davidmoten.rtree2.geometry.Point;
import com.github.davidmoten.rtree2.geometry.Rectangle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Demo: David Moten's <b>rtree2</b> R-tree library.
 *
 * <p>rtree2 is an immutable, functional R-tree.  Every mutation ({@code add},
 * {@code delete}) returns a new tree — the original is unchanged.  This makes
 * it safe to share across threads without locking.
 *
 * <p>Key types:
 * <ul>
 *   <li>{@link RTree} – the immutable tree (generic over value type &amp; geometry type)</li>
 *   <li>{@link Geometries#point(double, double)} – a 2-D point geometry</li>
 *   <li>{@link Geometries#rectangle(double, double, double, double)} – bounding box</li>
 *   <li>{@link Entry} – a (value, geometry) pair stored in the tree</li>
 * </ul>
 *
 * <p>Run: {@code mvn compile exec:java}
 */
public class Main {

    private static final com.example.rtree2.Place[] CITIES = {
        new com.example.rtree2.Place("Paris",     2.3522,  48.8566),
        new com.example.rtree2.Place("London",   -0.1276,  51.5074),
        new com.example.rtree2.Place("Tokyo",   139.6917,  35.6895),
        new com.example.rtree2.Place("New York", -74.0060, 40.7128),
        new com.example.rtree2.Place("Delhi",    77.2090,  28.6139),
        new com.example.rtree2.Place("Sydney",  151.2093, -33.8688),
        new com.example.rtree2.Place("Berlin",   13.4050,  52.5200),
        new com.example.rtree2.Place("Cairo",    31.2357,  30.0444),
        new com.example.rtree2.Place("Rio",     -43.1729, -22.9068),
        new com.example.rtree2.Place("Lagos",     3.3792,   6.5244),
    };

    public static void main(String[] args) {

        // ------------------------------------------------------------------ //
        // 1. Build the R-tree (immutable – each add() returns a new tree)
        // ------------------------------------------------------------------ //
        RTree<String, Point> tree = RTree.create();

        for (com.example.rtree2.Place p : CITIES) {
            tree = tree.add(p.name(), Geometries.point(p.lon(), p.lat()));
        }

        Random rnd = new Random(42);
        for (int i = 0; i < 500; i++) {
            double lon = rnd.nextDouble() * 360 - 180;
            double lat = rnd.nextDouble() * 180 - 90;
            tree = tree.add("Rnd-" + i, Geometries.point(lon, lat));
        }

        System.out.println("=== rtree2 (R-tree) Demo ===");
        System.out.printf("Total entries in tree : %d%n%n", tree.size());

        // ------------------------------------------------------------------ //
        // 2. Range query – bounding box around Europe
        // ------------------------------------------------------------------ //
        Rectangle europeRect = Geometries.rectangle(-10, 35, 30, 60);
        Iterable<Entry<String, Point>> europeHits = tree.search(europeRect);

        System.out.printf("--- Range query: Europe %s ---%n", europeRect);
        List<String> europeNamed = new ArrayList<>();
        for (Entry<String, Point> e : europeHits) {
            if (!e.value().startsWith("Rnd")) europeNamed.add(e.value());
        }
        europeNamed.forEach(n -> System.out.printf("  Named: %s%n", n));
        System.out.println();

        // ------------------------------------------------------------------ //
        // 3. Range query – tight box around Tokyo
        // ------------------------------------------------------------------ //
        Rectangle tokyoRect = Geometries.rectangle(138.0, 34.0, 141.0, 37.0);
        System.out.printf("--- Range query: Tokyo box ---%n");
        for (Entry<String, Point> e : tree.search(tokyoRect)) {
            System.out.printf("  %s @ (%.4f, %.4f)%n",
                e.value(), e.geometry().x(), e.geometry().y());
        }
        System.out.println();

        // ------------------------------------------------------------------ //
        // 4. Nearest-neighbour – closest 3 cities to Paris
        // ------------------------------------------------------------------ //
        Point paris = Geometries.point(2.3522, 48.8566);
        System.out.println("--- 3 nearest entries to Paris ---");
        Iterable<Entry<String, Point>> nearest = tree.nearest(paris, Double.MAX_VALUE, 3);
        for (Entry<String, Point> e : nearest) {
            double dist = euclidean(paris.x(), paris.y(),
                                   e.geometry().x(), e.geometry().y());
            System.out.printf("  %-12s  dist=%.4f°%n", e.value(), dist);
        }
        System.out.println();

        // ------------------------------------------------------------------ //
        // 5. Delete an entry (returns new tree; original is unchanged)
        // ------------------------------------------------------------------ //
        RTree<String, Point> treeAfterDelete = tree.delete(
            "Berlin", Geometries.point(13.4050, 52.5200));
        System.out.printf("--- Delete Berlin  |  before=%d  after=%d ---%n%n",
            tree.size(), treeAfterDelete.size());

        // ------------------------------------------------------------------ //
        // 6. Star (STR-packed) tree for bulk load – better for static data
        // ------------------------------------------------------------------ //
        List<Entry<String, Point>> entries = new ArrayList<>();
        for (com.example.rtree2.Place p : CITIES) {
            entries.add(com.github.davidmoten.rtree2.Entries.entry(
                p.name(), Geometries.point(p.lon(), p.lat())));
        }
        RTree<String, Point> starTree = RTree.star().create(entries);
        System.out.printf("--- STR-packed (star) tree  |  size=%d ---%n", starTree.size());
        System.out.println("  STR packing produces shallower trees with better query performance");
        System.out.println("  for static datasets compared to incremental insertion.");
        System.out.println();

        // ------------------------------------------------------------------ //
        // 7. rtree2 key concepts recap
        // ------------------------------------------------------------------ //
        System.out.println("=== rtree2 Key Concepts ===");
        System.out.println("  RTree.create()              – empty mutable R-tree");
        System.out.println("  RTree.star().create(list)   – bulk-load STR-packed tree (faster for static data)");
        System.out.println("  tree.add(value, geometry)   – returns NEW tree (immutable)");
        System.out.println("  tree.delete(value, geometry)– returns NEW tree (immutable)");
        System.out.println("  tree.search(rectangle)      – range query (Iterable<Entry>)");
        System.out.println("  tree.nearest(point, maxD, k)– k nearest-neighbour query");
        System.out.println("  Thread-safe: share the reference; mutations produce new trees.");
    }

    private static double euclidean(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
