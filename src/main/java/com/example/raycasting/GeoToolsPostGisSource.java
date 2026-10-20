package com.example.raycasting;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.filter.Filter;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GeoTools as a thin, database-independent layer over PostGIS.
 *
 * You write an ECQL filter, and the PostGIS DataStore translates it to SQL (ST_Intersects, bbox
 * operators) so the database's GiST index does the work. The same code would run unchanged
 * against a shapefile or GeoPackage DataStore.
 *
 * Reads an existing table. Create it with {@link PostGisGeofenceIndex} first.
 */
public final class GeoToolsPostGisSource implements AutoCloseable {

    private final DataStore dataStore;
    private final SimpleFeatureSource source;

    public GeoToolsPostGisSource(String table) {
        Map<String, Object> params = Map.of(
                "dbtype", "postgis",
                "host", PostGisConfig.HOST,
                "port", PostGisConfig.PORT,
                "database", PostGisConfig.DATABASE,
                "schema", "public",
                "user", PostGisConfig.USER,
                "passwd", PostGisConfig.PASSWORD);
        try {
            this.dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null) {
                throw new IllegalStateException("No GeoTools DataStore found for PostGIS (is gt-jdbc-postgis on the classpath?)");
            }
            this.source = dataStore.getFeatureSource(table);
        } catch (IOException e) {
            throw new IllegalStateException("Could not open PostGIS DataStore", e);
        }
    }

    /** Zone names matching an ECQL filter, e.g. {@code CONTAINS(geom, POINT(77.78 12.97))}. */
    public List<String> namesWhere(String ecql) {
        try {
            Filter filter = ECQL.toFilter(ecql);
            List<String> names = new ArrayList<>();
            try (SimpleFeatureIterator it = source.getFeatures(filter).features()) {
                while (it.hasNext()) {
                    SimpleFeature f = it.next();
                    names.add((String) f.getAttribute("name"));
                }
            }
            names.sort(String::compareTo);
            return names;
        } catch (CQLException | IOException e) {
            throw new IllegalStateException("Query failed: " + ecql, e);
        }
    }

    public List<String> zonesContaining(Point p) {
        return namesWhere("CONTAINS(geom, POINT(" + p.x() + " " + p.y() + "))");
    }

    /** Spatial filter and attribute filter combined in one query, still executed as a single SQL statement. */
    public List<String> zonesInBoxWithNameLike(double minLng, double minLat, double maxLng, double maxLat, String pattern) {
        return namesWhere("BBOX(geom, " + minLng + ", " + minLat + ", " + maxLng + ", " + maxLat + ") AND name LIKE '" + pattern + "'");
    }

    public int count() {
        try {
            return source.getCount(new org.geotools.api.data.Query(source.getSchema().getTypeName(), Filter.INCLUDE));
        } catch (IOException e) {
            throw new IllegalStateException("count failed", e);
        }
    }

    @Override
    public void close() {
        dataStore.dispose();
    }
}
