package com.example.quadtreejts;

/**
 * Simple place record used across JTS examples.
 */
public record Place(String name, double lon, double lat) {

    @Override
    public String toString() {
        return String.format("%-20s (lon=%.4f, lat=%.4f)", name, lon, lat);
    }
}
