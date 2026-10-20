package com.example.raycasting;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory geofence index built on GeoTools.
 *
 * GeoTools provides the feature model (SimpleFeature), OGC filters and CRS transforms. It builds on
 * JTS, which provides the STRtree (a packed R-tree) used here as the spatial index.
 *
 * Query = STRtree envelope search for candidates, then an OGC CONTAINS filter on each candidate.
 *
 * The STRtree is built on the first query. Add every zone before querying.
 */
public final class GeoToolsGeofenceIndex implements ZoneLocator {

    /** UTM zone 43N: a metric CRS that fits Bengaluru. Pick the UTM zone for your own region. */
    public static final String METRIC_CRS = "EPSG:32643";

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final FilterFactory FILTERS = CommonFactoryFinder.getFilterFactory();

    private static final SimpleFeatureType ZONE_TYPE = createType();

    private final STRtree tree = new STRtree();
    private final Map<String, SimpleFeature> features = new HashMap<>();
    private final MathTransform toMetric;

    public GeoToolsGeofenceIndex() {
        try {
            CoordinateReferenceSystem wgs84 = CRS.decode("EPSG:4326", true); // true = longitude first
            CoordinateReferenceSystem metric = CRS.decode(METRIC_CRS, true);
            this.toMetric = CRS.findMathTransform(wgs84, metric, true);
        } catch (FactoryException e) {
            throw new IllegalStateException("Could not build CRS transform", e);
        }
    }

    private static SimpleFeatureType createType() {
        try {
            return DataUtilities.createType("zone", "the_geom:Polygon:srid=4326,name:String");
        } catch (org.geotools.feature.SchemaException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void addZone(String name, List<Point> vertices) {
        Coordinate[] ring = new Coordinate[vertices.size() + 1];
        for (int i = 0; i < vertices.size(); i++) {
            ring[i] = new Coordinate(vertices.get(i).x(), vertices.get(i).y());
        }
        ring[vertices.size()] = ring[0];
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(ring);

        SimpleFeature feature = SimpleFeatureBuilder.build(ZONE_TYPE, new Object[]{polygon, name}, name);
        features.put(name, feature);
        tree.insert(polygon.getEnvelopeInternal(), feature);
    }

    @Override
    public Optional<String> findZone(Point p) {
        org.locationtech.jts.geom.Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(p.x(), p.y()));
        // OGC filter. The same filter object could be sent to a PostGIS DataStore and become SQL.
        Filter contains = FILTERS.contains(FILTERS.property("the_geom"), FILTERS.literal(point));

        @SuppressWarnings("unchecked")
        List<SimpleFeature> candidates = tree.query(new Envelope(p.x(), p.x(), p.y(), p.y()));
        List<String> hits = new ArrayList<>();
        for (SimpleFeature candidate : candidates) {
            if (contains.evaluate(candidate)) {
                hits.add((String) candidate.getAttribute("name"));
            }
        }
        hits.sort(String::compareTo); // deterministic when zones overlap, like ORDER BY name in PostGIS
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }

    /**
     * Distance in metres from the point to the zone. Both are reprojected from WGS84 degrees
     * into UTM so that JTS' planar distance is in metres. Accurate within the UTM zone.
     */
    public double distanceToZoneMeters(Point p, String zone) {
        SimpleFeature feature = features.get(zone);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown zone: " + zone);
        }
        try {
            Geometry zoneMetric = JTS.transform((Geometry) feature.getDefaultGeometry(), toMetric);
            Geometry pointMetric = JTS.transform(
                    GEOMETRY_FACTORY.createPoint(new Coordinate(p.x(), p.y())), toMetric);
            return zoneMetric.distance(pointMetric);
        } catch (TransformException e) {
            throw new IllegalStateException("CRS transform failed", e);
        }
    }

    public int zoneCount() {
        return features.size();
    }
}
