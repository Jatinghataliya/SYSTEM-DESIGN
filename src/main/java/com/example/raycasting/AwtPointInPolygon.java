package com.example.raycasting;

import java.awt.geom.Path2D;
import java.util.List;

/**
 * Zero-dependency option: the JDK's own java.awt.geom.Path2D.
 * Works headless (no GUI needed). Good enough for simple 2D checks.
 * Note: uses float/double 2D math, no geodesic support, no spatial index.
 */
public final class AwtPointInPolygon {

    private final Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);

    public AwtPointInPolygon(List<Point> vertices) {
        path.moveTo(vertices.get(0).x(), vertices.get(0).y());
        for (int i = 1; i < vertices.size(); i++) {
            path.lineTo(vertices.get(i).x(), vertices.get(i).y());
        }
        path.closePath();
    }

    public boolean contains(Point p) {
        return path.contains(p.x(), p.y());
    }
}
