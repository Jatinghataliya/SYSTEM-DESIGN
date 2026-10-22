package com.example.quadtreejts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.quadtree.Quadtree;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JTS {@link Quadtree} usage.
 */
class QuadtreeJtsTest {

    private Quadtree index;

    @BeforeEach
    void setUp() {
        index = new Quadtree();
    }

    // ------------------------------------------------------------------ //
    // Insert / size
    // ------------------------------------------------------------------ //

    @Test
    void insert_singleItem_sizeIsOne() {
        index.insert(pointEnv(0, 0), "Origin");
        assertEquals(1, index.size());
    }

    @Test
    void insert_multipleItems_sizeMatches() {
        index.insert(pointEnv(1, 1), "A");
        index.insert(pointEnv(2, 2), "B");
        index.insert(pointEnv(3, 3), "C");
        assertEquals(3, index.size());
    }

    // ------------------------------------------------------------------ //
    // Range query
    // ------------------------------------------------------------------ //

    @Test
    void query_emptyIndex_returnsEmptyList() {
        List<?> result = index.query(new Envelope(0, 10, 0, 10));
        assertTrue(result.isEmpty());
    }

    @Test
    void query_allItemsInRange_returnsAll() {
        index.insert(pointEnv(1, 1), "A");
        index.insert(pointEnv(2, 2), "B");
        Envelope big = new Envelope(-10, 10, -10, 10);
        // JTS may return false positives; all these are exact matches too
        List<?> result = index.query(big);
        assertEquals(2, exactCount(result, big));
    }

    @Test
    void query_noItemsInRange_returnsEmpty() {
        index.insert(pointEnv(100, 50), "Far");
        Envelope near = new Envelope(0, 5, 0, 5);
        List<?> result = index.query(near);
        assertEquals(0, exactCount(result, near));
    }

    @Test
    void query_partialRange_returnsSubset() {
        index.insert(pointEnv(1, 1), "In");
        index.insert(pointEnv(20, 20), "Out");
        Envelope range = new Envelope(-5, 5, -5, 5);
        assertEquals(1, exactCount(index.query(range), range));
    }

    @Test
    void query_boundaryPoint_included() {
        // JTS Envelope.contains is inclusive on boundary
        index.insert(pointEnv(10, 10), "Corner");
        Envelope range = new Envelope(0, 10, 0, 10);
        assertEquals(1, exactCount(index.query(range), range));
    }

    // ------------------------------------------------------------------ //
    // Remove
    // ------------------------------------------------------------------ //

    @Test
    void remove_existingItem_returnsTrueAndDecreasesSize() {
        Envelope env = pointEnv(5, 5);
        index.insert(env, "Item");
        boolean removed = index.remove(env, "Item");
        assertTrue(removed);
        assertEquals(0, index.size());
    }

    @Test
    void remove_nonExistentItem_returnsFalse() {
        Envelope env = pointEnv(5, 5);
        boolean removed = index.remove(env, "Ghost");
        assertFalse(removed);
    }

    @Test
    void remove_afterRemoval_itemNoLongerQueried() {
        Envelope env = pointEnv(1, 1);
        index.insert(env, "Temp");
        index.remove(env, "Temp");
        Envelope range = new Envelope(-5, 5, -5, 5);
        assertEquals(0, exactCount(index.query(range), range));
    }

    // ------------------------------------------------------------------ //
    // Known cities
    // ------------------------------------------------------------------ //

    @Test
    void query_europeBox_containsParisAndLondon() {
        insertCities();
        Envelope europe = new Envelope(-10, 30, 35, 60);
        List<?> hits = index.query(europe);
        long named = hits.stream()
            .filter(o -> {
                Place p = (Place) o;
                return europe.contains(new Coordinate(p.lon(), p.lat()))
                    && !p.name().startsWith("Rnd");
            })
            .count();
        assertTrue(named >= 2, "Should find at least Paris and London");
    }

    @Test
    void query_tokyoBox_containsTokyo() {
        insertCities();
        Envelope tokyoBox = new Envelope(138, 141, 34, 37);
        List<?> hits = index.query(tokyoBox);
        boolean found = hits.stream()
            .filter(o -> tokyoBox.contains(new Coordinate(((Place) o).lon(), ((Place) o).lat())))
            .anyMatch(o -> ((Place) o).name().equals("Tokyo"));
        assertTrue(found);
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    private static Envelope pointEnv(double x, double y) {
        return new Envelope(new Coordinate(x, y));
    }

    private static long exactCount(List<?> candidates, Envelope range) {
        return candidates.stream()
            .filter(o -> {
                if (o instanceof Place p) {
                    return range.contains(new Coordinate(p.lon(), p.lat()));
                }
                // For simple string items inserted with pointEnv we can't post-filter
                return true;
            })
            .count();
    }

    private void insertCities() {
        Place[] cities = {
            new Place("Paris",    2.3522,  48.8566),
            new Place("London",  -0.1276,  51.5074),
            new Place("Tokyo",  139.6917,  35.6895),
        };
        for (Place c : cities) {
            index.insert(pointEnv(c.lon(), c.lat()), c);
        }
    }
}
