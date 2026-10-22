package com.example.rtree2;

import com.github.davidmoten.rtree2.Entries;
import com.github.davidmoten.rtree2.Entry;
import com.github.davidmoten.rtree2.RTree;
import com.github.davidmoten.rtree2.geometry.Geometries;
import com.github.davidmoten.rtree2.geometry.Point;
import com.github.davidmoten.rtree2.geometry.Rectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the rtree2 {@link RTree} usage.
 */
class RTree2Test {

    private RTree<String, Point> tree;

    @BeforeEach
    void setUp() {
        tree = RTree.<String, Point>create()
            .add("Paris",    Geometries.point( 2.3522,  48.8566))
            .add("London",   Geometries.point(-0.1276,  51.5074))
            .add("Tokyo",    Geometries.point(139.6917, 35.6895))
            .add("New York", Geometries.point(-74.006,  40.7128))
            .add("Berlin",   Geometries.point( 13.405,  52.520));
    }

    // ------------------------------------------------------------------ //
    // Tree size / immutability
    // ------------------------------------------------------------------ //

    @Test
    void create_emptyTree_sizeIsZero() {
        assertEquals(0, RTree.create().size());
    }

    @Test
    void add_returnsNewTree_originalUnchanged() {
        RTree<String, Point> empty = RTree.create();
        RTree<String, Point> withOne = empty.add("A", Geometries.point(1, 1));
        assertEquals(0, empty.size());
        assertEquals(1, withOne.size());
    }

    @Test
    void add_fiveCities_sizeIsFive() {
        assertEquals(5, tree.size());
    }

    // ------------------------------------------------------------------ //
    // Range query
    // ------------------------------------------------------------------ //

    @Test
    void search_emptyTree_returnsNoEntries() {
        RTree<String, Point> empty = RTree.create();
        Rectangle rect = Geometries.rectangle(-10, 35, 30, 60);
        assertFalse(empty.search(rect).iterator().hasNext());
    }

    @Test
    void search_europeRectangle_containsParisAndLondon() {
        Rectangle europe = Geometries.rectangle(-10, 35, 30, 60);
        List<String> found = collect(tree.search(europe));
        assertTrue(found.contains("Paris"),  "Paris should be in Europe box");
        assertTrue(found.contains("London"), "London should be in Europe box");
        assertFalse(found.contains("Tokyo"), "Tokyo should NOT be in Europe box");
    }

    @Test
    void search_tokyoBox_containsTokyo() {
        Rectangle tokyoBox = Geometries.rectangle(138.0, 34.0, 141.0, 37.0);
        List<String> found = collect(tree.search(tokyoBox));
        assertTrue(found.contains("Tokyo"));
        assertFalse(found.contains("Paris"));
    }

    @Test
    void search_tightBox_returnsNone_whenEmpty() {
        Rectangle tiny = Geometries.rectangle(0, 0, 0.001, 0.001);
        List<String> found = collect(tree.search(tiny));
        assertTrue(found.isEmpty());
    }

    // ------------------------------------------------------------------ //
    // Nearest neighbour
    // ------------------------------------------------------------------ //

    @Test
    void nearest_1_toParis_returnsParis() {
        Point paris = Geometries.point(2.3522, 48.8566);
        List<String> found = collect(tree.nearest(paris, Double.MAX_VALUE, 1));
        assertEquals(1, found.size());
        assertEquals("Paris", found.get(0));
    }

    @Test
    void nearest_3_returnThreeClosest() {
        Point paris = Geometries.point(2.3522, 48.8566);
        List<String> found = collect(tree.nearest(paris, Double.MAX_VALUE, 3));
        assertEquals(3, found.size());
        // Paris, London and Berlin are the three closest to Paris
        assertTrue(found.contains("Paris"));
        assertTrue(found.contains("London"));
        assertTrue(found.contains("Berlin"));
    }

    @Test
    void nearest_maxDistanceLimits_results() {
        // Very small max distance — only Paris itself should match (distance = 0)
        Point paris = Geometries.point(2.3522, 48.8566);
        List<String> found = collect(tree.nearest(paris, 0.001, 5));
        assertEquals(1, found.size());
        assertEquals("Paris", found.get(0));
    }

    // ------------------------------------------------------------------ //
    // Delete (immutable)
    // ------------------------------------------------------------------ //

    @Test
    void delete_existing_returnsSmallerTree() {
        RTree<String, Point> after = tree.delete("Berlin", Geometries.point(13.405, 52.520));
        assertEquals(tree.size() - 1, after.size());
    }

    @Test
    void delete_original_unchanged() {
        tree.delete("Paris", Geometries.point(2.3522, 48.8566));
        // Original tree must still have Paris
        assertEquals(5, tree.size());
    }

    @Test
    void delete_then_searchExcludesDeleted() {
        RTree<String, Point> updated = tree.delete("London", Geometries.point(-0.1276, 51.5074));
        Rectangle europe = Geometries.rectangle(-10, 35, 30, 60);
        List<String> found = collect(updated.search(europe));
        assertFalse(found.contains("London"), "London should have been deleted");
        assertTrue(found.contains("Paris"), "Paris should still be there");
    }

    // ------------------------------------------------------------------ //
    // STR-packed (star) tree
    // ------------------------------------------------------------------ //

    @Test
    void starTree_bulkLoad_correctSize() {
        List<Entry<String, Point>> entries = new ArrayList<>();
        entries.add(Entries.entry("A", Geometries.point(1, 1)));
        entries.add(Entries.entry("B", Geometries.point(2, 2)));
        entries.add(Entries.entry("C", Geometries.point(3, 3)));
        RTree<String, Point> starTree = RTree.star().create(entries);
        assertEquals(3, starTree.size());
    }

    @Test
    void starTree_search_producesCorrectResults() {
        List<Entry<String, Point>> entries = List.of(
            Entries.entry("Paris",   Geometries.point( 2.3522,  48.8566)),
            Entries.entry("Tokyo",   Geometries.point(139.6917, 35.6895))
        );
        RTree<String, Point> starTree = RTree.star().create(entries);
        Rectangle europe = Geometries.rectangle(-10, 35, 30, 60);
        List<String> found = collect(starTree.search(europe));
        assertTrue(found.contains("Paris"));
        assertFalse(found.contains("Tokyo"));
    }

    // ------------------------------------------------------------------ //
    // Helper
    // ------------------------------------------------------------------ //

    private static <V> List<V> collect(Iterable<Entry<V, Point>> iterable) {
        List<V> list = new ArrayList<>();
        for (Entry<V, Point> e : iterable) list.add(e.value());
        return list;
    }
}
