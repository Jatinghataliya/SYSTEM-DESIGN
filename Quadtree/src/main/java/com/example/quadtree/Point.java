package com.example.quadtree;

/**
 * Immutable 2-D point with an optional label.
 */
public final class Point {

    public final double x;
    public final double y;
    public final String label;

    public Point(double x, double y, String label) {
        this.x = x;
        this.y = y;
        this.label = label;
    }

    public Point(double x, double y) {
        this(x, y, "(" + x + "," + y + ")");
    }

    @Override
    public String toString() {
        return label + " @ (" + x + ", " + y + ")";
    }
}
