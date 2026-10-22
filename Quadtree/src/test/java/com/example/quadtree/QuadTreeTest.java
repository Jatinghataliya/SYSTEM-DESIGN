package com.example.quadtree;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QuadTree}.
 */
class QuadTreeTest {

    // Tree covering whole lon/lat space: centre (0,0), half-extents 180 x 90
    private QuadTree tree;

    @BeforeEach
    void setUp() {
        tree = new QuadTree(0, 0, 180, 90, 4);
    }

    // -----------------------------------------------------------------------
    // BoundingBox tests
    // -----------------------------------------------------------------------

    @Test
    void boundingBox_containsPoint_inside() {
        BoundingBox box = new BoundingBox(0, 0, 10, 10);
        assertTrue(box.contains(new Point(5, 5)));
        assertTrue(box.contains(new Point(-10, -10)));  // boundary
        assertTrue(box.contains(new Point(0, 0)));       // centre
    }

    @Test
    void boundingBox_doesNotContain_outside() {
        BoundingBox box = new BoundingBox(0, 0, 10, 10);
        assertFalse(box.contains(new Point(11, 0)));
        assertFalse(box.contains(new Point(0, -11)));
    }

    @Test
    void boundingBox_intersects_overlapping() {
        BoundingBox a = new BoundingBox(0, 0, 10, 10);
        BoundingBox b = new BoundingBox(5, 5, 10, 10);
        assertTrue(a.intersects(b));
        assertTrue(b.intersects(a));
    }

    @Test
    void boundingBox_doesNotIntersect_disjoint() {
        BoundingBox a = new BoundingBox(0, 0, 5, 5);
        BoundingBox b = new BoundingBox(20, 20, 5, 5);
        assertFalse(a.intersects(b));
    }

    @Test
    void boundingBox_subdivide_fourChildren() {
        BoundingBox box = new BoundingBox(0, 0, 10, 10);
        BoundingBox[] quads = box.subdivide();
        assertEquals(4, quads.length);

        // NW quadrant: cx=-5, cy=+5, hw=5, hh=5
        BoundingBox nw = quads[0];
        assertEquals(-5.0, nw.cx, 1e-9);
        assertEquals(5.0,  nw.cy, 1e-9);
        assertEquals(5.0,  nw.hw, 1e-9);
        assertEquals(5.0,  nw.hh, 1e-9);

        // NE quadrant: cx=+5, cy=+5
        BoundingBox ne = quads[1];
        assertEquals(5.0, ne.cx, 1e-9);
        assertEquals(5.0, ne.cy, 1e-9);
    }

    // -----------------------------------------------------------------------
    // Insert tests
    // -----------------------------------------------------------------------

    @Test
    void insert_singlePoint_sizeIsOne() {
        tree.insert(new Point(10, 20, "A"));
        assertEquals(1, tree.size());
    }

    @Test
    void insert_outsideBounds_returnsFalse() {
        boolean ok = tree.insert(new Point(200, 0, "Outside"));
        assertFalse(ok);
        assertEquals(0, tree.size());
    }

    @Test
    void insert_manyPoints_allCounted() {
        for (int i = 0; i < 100; i++) {
            tree.insert(new Point(i % 360 - 180, i % 180 - 90, "P" + i));
        }
        assertEquals(100, tree.size());
    }

    @Test
    void insert_triggersSplit_treeGrows() {
        // Capacity 4 — inserting 5 points in the same region must trigger a split
        QuadTree small = new QuadTree(0, 0, 10, 10, 4);
        for (int i = 0; i < 5; i++) {
            small.insert(new Point(i * 0.1, i * 0.1, "P" + i));
        }
        assertEquals(5, small.size());
        assertTrue(small.nodeCount() > 1, "Tree should have split into children");
    }

    // -----------------------------------------------------------------------
    // Range query tests
    // -----------------------------------------------------------------------

    @Test
    void query_emptyTree_returnsEmptyList() {
        BoundingBox range = new BoundingBox(0, 0, 5, 5);
        assertTrue(tree.query(range).isEmpty());
    }

    @Test
    void query_allPointsInside_returnsAll() {
        tree.insert(new Point(1, 1, "A"));
        tree.insert(new Point(-1, -1, "B"));
        tree.insert(new Point(0, 0, "C"));

        BoundingBox big = new BoundingBox(0, 0, 10, 10);
        assertEquals(3, tree.query(big).size());
    }

    @Test
    void query_noPointsInRange_returnsEmpty() {
        tree.insert(new Point(100, 50, "Far"));
        BoundingBox range = new BoundingBox(0, 0, 5, 5);
        assertTrue(tree.query(range).isEmpty());
    }

    @Test
    void query_partialOverlap_returnsCorrectSubset() {
        tree.insert(new Point(3, 3, "In"));
        tree.insert(new Point(15, 5, "Out"));
        tree.insert(new Point(8, 5, "Out2"));

        // Box covering x in [-5, 5], y in [-5, 5]  -> only "In" at (3,3) qualifies
        BoundingBox range = new BoundingBox(0, 0, 5, 5);
        List<Point> result = tree.query(range);
        assertEquals(1, result.size());
        assertEquals("In", result.get(0).label);
    }

    @Test
    void query_boundaryPoints_included() {
        // Points exactly on the boundary of the query range must be returned
        tree.insert(new Point(10, 10, "Corner"));
        BoundingBox range = new BoundingBox(0, 0, 10, 10);
        List<Point> result = tree.query(range);
        assertEquals(1, result.size());
        assertEquals("Corner", result.get(0).label);
    }

    @Test
    void query_afterSplit_stillFindsAll() {
        // Force several splits by inserting clustered points
        QuadTree dense = new QuadTree(0, 0, 100, 100, 2);
        int count = 50;
        for (int i = 0; i < count; i++) {
            dense.insert(new Point(i * 0.5, i * 0.5, "P" + i));
        }
        BoundingBox all = new BoundingBox(0, 0, 100, 100);
        assertEquals(count, dense.query(all).size());
    }

    // -----------------------------------------------------------------------
    // Nearest-neighbour tests
    // -----------------------------------------------------------------------

    @Test
    void findNearest_emptyTree_returnsNull() {
        assertNull(tree.findNearest(0, 0));
    }

    @Test
    void findNearest_singlePoint_returnsIt() {
        tree.insert(new Point(10, 20, "Only"));
        assertEquals("Only", tree.findNearest(10, 20).label);
    }

    @Test
    void findNearest_multiplePoints_returnsClosest() {
        tree.insert(new Point(0, 0, "Origin"));
        tree.insert(new Point(100, 0, "Far"));
        tree.insert(new Point(1, 1, "Close"));

        Point nearest = tree.findNearest(0.5, 0.5);
        assertEquals("Origin", nearest.label);
    }

    @Test
    void findNearest_queryExactlyOnPoint_returnsThatPoint() {
        tree.insert(new Point(42, 42, "Exact"));
        tree.insert(new Point(50, 50, "Other"));
        assertEquals("Exact", tree.findNearest(42, 42).label);
    }

    @Test
    void findNearest_acrossSplitBoundary_stillCorrect() {
        // Points on opposite sides of a split axis
        QuadTree t = new QuadTree(0, 0, 100, 100, 1);
        t.insert(new Point(-1, 0, "Left"));
        t.insert(new Point(1, 0, "Right"));
        // Query very slightly left of 0 -> "Left" should win
        assertEquals("Left", t.findNearest(-0.1, 0).label);
        assertEquals("Right", t.findNearest(0.1, 0).label);
    }

    // -----------------------------------------------------------------------
    // Stress / equivalence test
    // -----------------------------------------------------------------------

    @Test
    void stressTest_queryMatchesBruteForce() {
        Random rnd = new Random(7);
        int n = 2_000;
        List<Point> all = new ArrayList<>(n);
        QuadTree big = new QuadTree(0, 0, 180, 90, 8);

        for (int i = 0; i < n; i++) {
            double x = rnd.nextDouble() * 360 - 180;
            double y = rnd.nextDouble() * 180 - 90;
            Point p = new Point(x, y, "P" + i);
            all.add(p);
            big.insert(p);
        }

        // Run 20 random range queries and compare with brute force
        for (int q = 0; q < 20; q++) {
            double cx = rnd.nextDouble() * 300 - 150;
            double cy = rnd.nextDouble() * 150 - 75;
            double hw = rnd.nextDouble() * 20 + 1;
            double hh = rnd.nextDouble() * 10 + 1;
            BoundingBox range = new BoundingBox(cx, cy, hw, hh);

            List<Point> treeResult = big.query(range);
            long bruteCount = all.stream().filter(range::contains).count();
            assertEquals(bruteCount, treeResult.size(),
                "Mismatch for range " + range);
        }
    }

    @Test
    void stressTest_nearestMatchesBruteForce() {
        Random rnd = new Random(13);
        int n = 1_000;
        List<Point> all = new ArrayList<>(n);
        QuadTree big = new QuadTree(0, 0, 180, 90, 4);

        for (int i = 0; i < n; i++) {
            double x = rnd.nextDouble() * 360 - 180;
            double y = rnd.nextDouble() * 180 - 90;
            Point p = new Point(x, y, "P" + i);
            all.add(p);
            big.insert(p);
        }

        // 50 random nearest-neighbour queries
        for (int q = 0; q < 50; q++) {
            double qx = rnd.nextDouble() * 360 - 180;
            double qy = rnd.nextDouble() * 180 - 90;

            Point treeNearest = big.findNearest(qx, qy);
            Point bruteNearest = all.stream()
                .min((a, b) -> Double.compare(dist2(a, qx, qy), dist2(b, qx, qy)))
                .orElse(null);

            assertNotNull(treeNearest);
            assertNotNull(bruteNearest);
            assertEquals(dist2(bruteNearest, qx, qy), dist2(treeNearest, qx, qy), 1e-9,
                "Nearest-neighbour mismatch at query (" + qx + ", " + qy + ")");
        }
    }

    // -----------------------------------------------------------------------
    // Tree statistics
    // -----------------------------------------------------------------------

    @Test
    void stats_emptyTree() {
        assertEquals(0, tree.size());
        assertEquals(1, tree.nodeCount()); // just the root leaf
        assertEquals(1, tree.maxDepth());
    }

    @Test
    void stats_afterInsert_nodeCountIncreases() {
        QuadTree t = new QuadTree(0, 0, 10, 10, 1);
        t.insert(new Point(1, 1, "A"));
        t.insert(new Point(-1, 1, "B"));
        // Two points in capacity-1 tree -> must have split at least once
        assertTrue(t.nodeCount() > 1);
        assertTrue(t.maxDepth() > 1);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static double dist2(Point p, double qx, double qy) {
        double dx = p.x - qx, dy = p.y - qy;
        return dx * dx + dy * dy;
    }
}
