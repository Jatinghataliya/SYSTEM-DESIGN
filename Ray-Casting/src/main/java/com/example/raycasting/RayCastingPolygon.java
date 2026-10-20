package com.example.raycasting;

import java.util.List;

/**
 * Hand-written ray casting (even-odd rule) point-in-polygon test.
 *
 * A ray is cast from the point towards +x. Each polygon edge it crosses flips
 * the "inside" flag. Odd crossings = inside, even = outside.
 *
 * Time: O(n) per query, n = number of vertices. Space: O(1).
 * Points exactly on the boundary are not guaranteed to be classified consistently.
 */
public final class RayCastingPolygon {

    private final List<Point> vertices;

    public RayCastingPolygon(List<Point> vertices) {
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("A polygon needs at least 3 vertices");
        }
        this.vertices = List.copyOf(vertices);
    }

    public boolean contains(Point p) {
        boolean inside = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            Point a = vertices.get(j);
            Point b = vertices.get(i);

            // Half-open check: edge straddles the ray's y. Handles rays through a vertex consistently.
            boolean straddles = (a.y() > p.y()) != (b.y() > p.y());
            if (straddles) {
                // x coordinate where the edge crosses the horizontal line y = p.y
                double xAtY = (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y()) + a.x();
                if (p.x() < xAtY) {
                    inside = !inside; // crossing is to the right of the point
                }
            }
        }
        return inside;
    }

    /** Smallest axis-aligned box around the polygon, used as a cheap pre-filter. */
    public BoundingBox boundingBox() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Point v : vertices) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
        }
        return new BoundingBox(minX, minY, maxX, maxY);
    }

    public List<Point> vertices() {
        return vertices;
    }

    public record BoundingBox(double minX, double minY, double maxX, double maxY) {
        public boolean contains(Point p) {
            return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY;
        }
    }
}
