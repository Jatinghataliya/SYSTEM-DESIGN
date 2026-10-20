package com.example.raycasting;

import java.sql.Connection;
import java.sql.DriverManager;

/** Connection settings for the PostGIS container defined in docker-compose.yml. */
public final class PostGisConfig {

    public static final String HOST = "localhost";
    public static final int PORT = 5433;
    public static final String DATABASE = "geofence";
    public static final String USER = "geo";
    public static final String PASSWORD = "geo";
    public static final String JDBC_URL = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DATABASE;

    private PostGisConfig() {
    }

    public static Connection connect() throws java.sql.SQLException {
        return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }

    /** True if the container is up. Demos and tests use this to skip instead of failing. */
    public static boolean isAvailable() {
        try (Connection c = connect()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
