package com.example.quadtree;

import java.util.ArrayList;
import java.util.List;

/**
 * Point-Region (PR) Quadtree.
 *
 * <p>Each leaf node stores up to {@code capacity} points. When a leaf overflows
 * it is subdivided into four child quadrants and its points are redistributed.
 *
 * <p>Operations:
 * <ul>
 *   <li>{@link #insert(Point)} — O(log n) average, O(n) worst case
 *   <li>{@link #query(BoundingBox)} — O(log n + k) where k = result count
 *   <li>{@link #findNearest(double, double)} — O(log n) average
 *   <li>{@link #size()} / {@link #nodeCount()} / {@link #maxDepth()} — statistics
 * </ul>
 */
public class QuadTree {

    // -----------------------------------------------------------------------
    // Node (inner class)
    // -----------------------------------------------------------------------

    private static final class Node {
        final BoundingBox bounds;
        final int capacity;

        final List<Point> points;
        Node[] children; // null until subdivided; order: NW, NE, SW, SE

        Node(BoundingBox bounds, int capacity) {
            this.bounds = bounds;
            this.capacity = capacity;
            this.points = new ArrayList<>(capacity);
        }

        boolean isLeaf() {
            return children == null;
        }

        /** Split this node into four children and redistribute stored points. */
        void subdivide() {
            BoundingBox[] quads = bounds.subdivide();
            children = new Node[4];
            for (int i = 0; i < 4; i++) {
                children[i] = new Node(quads[i], capacity);
            }
            for (Point p : points) {
                for (Node child : children) {
                    if (child.insert(p)) break;
                }
            }
            points.clear();
        }

        /** Insert a point into this node (or its subtree). Returns true on success. */
        boolean insert(Point p) {
            if (!bounds.contains(p)) return false;

            if (isLeaf()) {
                if (points.size() < capacity) {
                    points.add(p);
                    return true;
                }
                // Leaf is full — subdivide and retry
                subdivide();
            }

            // Delegate to the correct child
            for (Node child : children) {
                if (child.insert(p)) return true;
            }
            // Should never happen for a valid bounding box, but guard anyway
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // QuadTree fields
    // -----------------------------------------------------------------------

    private final Node root;
    private int size;

    /**
     * Creates a Quadtree covering the rectangle centred at (cx, cy) with
     * half-extents (hw, hh) and the given leaf capacity.
     *
     * @param cx       centre x of the root bounding box
     * @param cy       centre y of the root bounding box
     * @param hw       half-width of the root bounding box
     * @param hh       half-height of the root bounding box
     * @param capacity maximum number of points stored in a leaf before it splits
     */
    public QuadTree(double cx, double cy, double hw, double hh, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        this.root = new Node(new BoundingBox(cx, cy, hw, hh), capacity);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Insert a point into the tree.
     *
     * @param p the point to insert
     * @return true if the point was inside the root bounding box and inserted;
     *         false if the point lies outside the tree's area
     */
    public boolean insert(Point p) {
        boolean inserted = root.insert(p);
        if (inserted) size++;
        return inserted;
    }

    /**
     * Return all points whose coordinates lie within {@code range}.
     *
     * @param range the query rectangle
     * @return list of points (may be empty, never null)
     */
    public List<Point> query(BoundingBox range) {
        List<Point> result = new ArrayList<>();
        queryNode(root, range, result);
        return result;
    }

    /**
     * Return the single point nearest to (qx, qy).
     * Returns {@code null} if the tree is empty.
     *
     * @param qx query x
     * @param qy query y
     * @return closest point, or null
     */
    public Point findNearest(double qx, double qy) {
        if (size == 0) return null;
        NearestHolder holder = new NearestHolder();
        nearestNode(root, qx, qy, holder);
        return holder.point;
    }

    /** Number of points stored in the tree. */
    public int size() { return size; }

    /** Total number of nodes (internal + leaf) in the tree. */
    public int nodeCount() { return countNodes(root); }

    /** Maximum depth of the tree (root = depth 1). */
    public int maxDepth() { return depth(root); }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void queryNode(Node node, BoundingBox range, List<Point> result) {
        if (!node.bounds.intersects(range)) return; // prune

        for (Point p : node.points) {
            if (range.contains(p)) result.add(p);
        }

        if (!node.isLeaf()) {
            for (Node child : node.children) {
                queryNode(child, range, result);
            }
        }
    }

    // Helper to track nearest result during recursive search
    private static final class NearestHolder {
        Point point;
        double bestDist2 = Double.MAX_VALUE;
    }

    private void nearestNode(Node node, double qx, double qy, NearestHolder h) {
        // If the closest possible point in this node's box is farther than the
        // current best, prune the entire subtree.
        if (minDist2ToBox(node.bounds, qx, qy) >= h.bestDist2) return;

        for (Point p : node.points) {
            double d2 = dist2(p.x, p.y, qx, qy);
            if (d2 < h.bestDist2) {
                h.bestDist2 = d2;
                h.point = p;
            }
        }

        if (!node.isLeaf()) {
            // Visit children ordered by distance to the query point (closest first)
            // so pruning is maximally effective.
            Node[] sorted = node.children.clone();
            // Simple insertion sort on 4 elements
            for (int i = 1; i < sorted.length; i++) {
                Node key = sorted[i];
                double kd = minDist2ToBox(key.bounds, qx, qy);
                int j = i - 1;
                while (j >= 0 && minDist2ToBox(sorted[j].bounds, qx, qy) > kd) {
                    sorted[j + 1] = sorted[j];
                    j--;
                }
                sorted[j + 1] = key;
            }
            for (Node child : sorted) {
                nearestNode(child, qx, qy, h);
            }
        }
    }

    /** Squared Euclidean distance between two points. */
    private static double dist2(double ax, double ay, double bx, double by) {
        double dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    /** Minimum squared distance from point (qx, qy) to the boundary of bbox b. */
    private static double minDist2ToBox(BoundingBox b, double qx, double qy) {
        double dx = Math.max(0, Math.max(b.cx - b.hw - qx, qx - (b.cx + b.hw)));
        double dy = Math.max(0, Math.max(b.cy - b.hh - qy, qy - (b.cy + b.hh)));
        return dx * dx + dy * dy;
    }

    private int countNodes(Node n) {
        if (n.isLeaf()) return 1;
        int count = 1;
        for (Node child : n.children) count += countNodes(child);
        return count;
    }

    private int depth(Node n) {
        if (n.isLeaf()) return 1;
        int max = 0;
        for (Node child : n.children) max = Math.max(max, depth(child));
        return 1 + max;
    }
}
