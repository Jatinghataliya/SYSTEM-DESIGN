package com.example.raycasting;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * PostGIS geofence index. The polygon test (GEOS) and the spatial index (GiST, an R-tree) both
 * run inside the database, so the application only sends a point and gets a zone name back.
 *
 * Geometry is stored as SRID 4326 (WGS84 lon/lat). The planar functions (ST_Intersects) treat
 * the coordinates as flat, like the ray casting code. The geography functions (ST_Distance,
 * ST_DWithin on ::geography) work on the spheroid and return metres.
 *
 * Not thread-safe: one JDBC connection. Use a connection pool in a real service.
 */
public final class PostGisGeofenceIndex implements ZoneLocator, AutoCloseable {

    private static final Pattern SAFE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

    private final Connection connection;
    private final String table;

    public PostGisGeofenceIndex(String table) {
        if (!SAFE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Unsafe table name: " + table);
        }
        this.table = table;
        try {
            this.connection = PostGisConfig.connect();
            createSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not set up PostGIS table " + table, e);
        }
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            st.execute("DROP TABLE IF EXISTS " + table);
            st.execute("CREATE TABLE " + table + " ("
                    + "id SERIAL PRIMARY KEY, "
                    + "name TEXT NOT NULL, "
                    + "geom geometry(Polygon, 4326) NOT NULL)");
            // GiST = R-tree style index over bounding boxes. Without it every query scans every zone.
            st.execute("CREATE INDEX " + table + "_geom_gix ON " + table + " USING GIST (geom)");
        }
    }

    @Override
    public void addZone(String name, List<Point> vertices) {
        String sql = "INSERT INTO " + table + " (name, geom) VALUES (?, ST_SetSRID(ST_GeomFromText(?), 4326))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, toWkt(vertices));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert zone " + name, e);
        }
    }

    /**
     * Inserts rows x cols square zones with one server-side statement, for scale tests.
     * Zone names look like "cell-r-c".
     */
    public void generateGridZones(int rows, int cols, double originLng, double originLat, double size) {
        String sql = "INSERT INTO " + table + " (name, geom) "
                + "SELECT 'cell-' || r || '-' || c, "
                + "ST_SetSRID(ST_MakeEnvelope(?::float8 + c * ?::float8, ?::float8 + r * ?::float8, "
                + "?::float8 + (c + 1) * ?::float8, ?::float8 + (r + 1) * ?::float8), 4326) "
                + "FROM generate_series(0, ?::int - 1) r, generate_series(0, ?::int - 1) c";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            ps.setDouble(i++, originLng);
            ps.setDouble(i++, size);
            ps.setDouble(i++, originLat);
            ps.setDouble(i++, size);
            ps.setDouble(i++, originLng);
            ps.setDouble(i++, size);
            ps.setDouble(i++, originLat);
            ps.setDouble(i++, size);
            ps.setInt(i++, rows);
            ps.setInt(i, cols);
            ps.executeUpdate();
            try (Statement st = connection.createStatement()) {
                st.execute("ANALYZE " + table); // refresh planner statistics so it picks the GiST index
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not generate grid zones", e);
        }
    }

    /** Planar point-in-polygon through the GiST index. Boundary points count as inside. */
    @Override
    public Optional<String> findZone(Point p) {
        String sql = "SELECT name FROM " + table
                + " WHERE ST_Intersects(geom, ST_SetSRID(ST_MakePoint(?, ?), 4326))"
                + " ORDER BY name LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, p.x());
            ps.setDouble(2, p.y());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findZone failed", e);
        }
    }

    /** Distance in metres from the point to the zone, computed on the spheroid (0 if inside). */
    public double distanceToZoneMeters(Point p, String zone) {
        String sql = "SELECT ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)"
                + " FROM " + table + " WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, p.x());
            ps.setDouble(2, p.y());
            ps.setString(3, zone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Unknown zone: " + zone);
                }
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("distance failed", e);
        }
    }

    /** Zones whose boundary is within the given number of metres of the point (includes zones containing it). */
    public List<String> zonesWithinMeters(Point p, double meters) {
        String sql = "SELECT name FROM " + table
                + " WHERE ST_DWithin(geom::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)"
                + " ORDER BY name";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, p.x());
            ps.setDouble(2, p.y());
            ps.setDouble(3, meters);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("zonesWithinMeters failed", e);
        }
        return result;
    }

    /** The k nearest zones by planar distance, using the GiST-assisted KNN operator (<->). */
    public List<String> nearestZones(Point p, int k) {
        String sql = "SELECT name FROM " + table
                + " ORDER BY geom <-> ST_SetSRID(ST_MakePoint(?, ?), 4326) LIMIT ?";
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, p.x());
            ps.setDouble(2, p.y());
            ps.setInt(3, k);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("nearestZones failed", e);
        }
        return result;
    }

    /** Drops the GiST index so a demo can show the cost of a sequential scan. */
    public void dropSpatialIndex() {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP INDEX IF EXISTS " + table + "_geom_gix");
        } catch (SQLException e) {
            throw new IllegalStateException("drop index failed", e);
        }
    }

    public void createSpatialIndex() {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS " + table + "_geom_gix ON " + table + " USING GIST (geom)");
            st.execute("ANALYZE " + table);
        } catch (SQLException e) {
            throw new IllegalStateException("create index failed", e);
        }
    }

    /** The query plan for findZone. Shows whether the GiST index is used. */
    public List<String> explain(Point p) {
        String sql = "EXPLAIN SELECT name FROM " + table
                + " WHERE ST_Intersects(geom, ST_SetSRID(ST_MakePoint(" + p.x() + ", " + p.y() + "), 4326))";
        List<String> plan = new ArrayList<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                plan.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("explain failed", e);
        }
        return plan;
    }

    public long zoneCount() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("count failed", e);
        }
    }

    public String tableName() {
        return table;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // nothing useful to do on close
        }
    }

    /** WKT polygon with a closed ring, in "lng lat" order. */
    static String toWkt(List<Point> vertices) {
        StringBuilder sb = new StringBuilder("POLYGON((");
        for (Point v : vertices) {
            sb.append(v.x()).append(' ').append(v.y()).append(", ");
        }
        Point first = vertices.get(0);
        sb.append(first.x()).append(' ').append(first.y()).append("))");
        return sb.toString();
    }
}
