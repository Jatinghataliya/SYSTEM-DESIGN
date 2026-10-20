package com.example.raycasting;

import org.locationtech.jts.algorithm.PointLocation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.List;

/**
 * Library-based point-in-polygon using JTS (Java Topology Suite).
 * JTS implements ray crossing internally (RayCrossingCounter) and also handles
 * boundary points, holes and robust arithmetic.
 */
public final class JtsPointInPolygon {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private final Coordinate[] ring;
    private final PreparedGeometry prepared;

    public JtsPointInPolygon(List<Point> vertices) {
        this.ring = toClosedRing(vertices);
        Polygon polygon = FACTORY.createPolygon(ring);
        // Prepared geometry builds an index once, so repeated queries are fast (~O(log n)).
        this.prepared = PreparedGeometryFactory.prepare(polygon);
    }

    /** Low-level API: uses the ray crossing counter directly. Boundary counts as inside. */
    public boolean containsRayCrossing(Point p) {
        return PointLocation.isInRing(new Coordinate(p.x(), p.y()), ring);
    }

    /** Distinguishes INTERIOR / BOUNDARY / EXTERIOR instead of a plain boolean. */
    public String locate(Point p) {
        int location = PointLocation.locateInRing(new Coordinate(p.x(), p.y()), ring);
        return switch (location) {
            case Location.INTERIOR -> "INTERIOR";
            case Location.BOUNDARY -> "BOUNDARY";
            default -> "EXTERIOR";
        };
    }

    /** High-level API: indexed (prepared) geometry, best for many queries on one polygon. */
    public boolean contains(Point p) {
        Geometry point = FACTORY.createPoint(new Coordinate(p.x(), p.y()));
        return prepared.contains(point);
    }

    private static Coordinate[] toClosedRing(List<Point> vertices) {
        Coordinate[] ring = new Coordinate[vertices.size() + 1];
        for (int i = 0; i < vertices.size(); i++) {
            ring[i] = new Coordinate(vertices.get(i).x(), vertices.get(i).y());
        }
        ring[vertices.size()] = ring[0]; // JTS rings must be closed
        return ring;
    }
}
