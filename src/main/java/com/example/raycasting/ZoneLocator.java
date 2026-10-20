package com.example.raycasting;

import java.util.List;
import java.util.Optional;

/**
 * Common contract for the cell-based indexes. Points use x = longitude, y = latitude.
 */
public interface ZoneLocator {

    void addZone(String name, List<Point> vertices);

    Optional<String> findZone(Point p);
}
