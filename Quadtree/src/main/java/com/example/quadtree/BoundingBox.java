package com.example.quadtree;

/**
 * Axis-aligned bounding box used to define the region of a Quadtree node.
 *
 * <p>The rectangle is defined by its centre (cx, cy) and half-widths (hw, hh).
 * This representation makes subdivision arithmetic clean.
 */
public final class BoundingBox {

    public final double cx;   // centre x
    public final double cy;   // centre y
    public final double hw;   // half-width  (x extent)
    public final double hh;   // half-height (y extent)

    public BoundingBox(double cx, double cy, double hw, double hh) {
        this.cx = cx;
        this.cy = cy;
        this.hw = hw;
        this.hh = hh;
    }

    /** True if the point is inside (inclusive boundary). */
    public boolean contains(Point p) {
        return p.x >= cx - hw && p.x <= cx + hw
            && p.y >= cy - hh && p.y <= cy + hh;
    }

    /** True if this box overlaps another (inclusive boundary). */
    public boolean intersects(BoundingBox other) {
        return !(other.cx - other.hw > cx + hw
              || other.cx + other.hw < cx - hw
              || other.cy - other.hh > cy + hh
              || other.cy + other.hh < cy - hh);
    }

    /**
     * Returns the four child quadrants in order: NW, NE, SW, SE.
     *
     * <pre>
     *  NW | NE
     *  ---+---
     *  SW | SE
     * </pre>
     */
    public BoundingBox[] subdivide() {
        double qw = hw / 2.0;
        double qh = hh / 2.0;
        return new BoundingBox[]{
            new BoundingBox(cx - qw, cy + qh, qw, qh), // NW
            new BoundingBox(cx + qw, cy + qh, qw, qh), // NE
            new BoundingBox(cx - qw, cy - qh, qw, qh), // SW
            new BoundingBox(cx + qw, cy - qh, qw, qh)  // SE
        };
    }

    @Override
    public String toString() {
        return String.format("BBox[cx=%.4f cy=%.4f hw=%.4f hh=%.4f]", cx, cy, hw, hh);
    }
}
