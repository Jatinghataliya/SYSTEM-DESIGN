package com.example.geohash;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        encodeAndDecode();
        prefixProperty();
        neighbours();
        proximitySearch();
    }

    private static void encodeAndDecode() {
        System.out.println("=== 1. Encode / decode ===");
        double lat = 48.8584, lon = 2.2945;   // Eiffel Tower
        for (int p = 1; p <= 9; p++) {
            String hash = GeoHash.encode(lat, lon, p);
            GeoHash.Bounds b = GeoHash.decode(hash);
            System.out.printf("precision %d  %-9s  cell ~ %,.0f m x %,.0f m%n", p, hash,
                    b.height() * 111_320.0,
                    b.width() * 111_320.0 * Math.cos(Math.toRadians(lat)));
        }
    }

    private static void prefixProperty() {
        System.out.println("\n=== 2. Shared prefix = proximity ===");
        String eiffel = GeoHash.encode(48.8584, 2.2945, 8);
        String louvre = GeoHash.encode(48.8606, 2.3376, 8);
        String tokyo  = GeoHash.encode(35.6586, 139.7454, 8);
        System.out.println("Eiffel Tower : " + eiffel);
        System.out.println("Louvre       : " + louvre + "  (same city -> shares prefix 'u09')");
        System.out.println("Tokyo Tower  : " + tokyo + "  (far away -> different prefix)");
    }

    private static void neighbours() {
        System.out.println("\n=== 3. Neighbours of u09tun ===");
        GeoHash.neighbors("u09tun").forEach((d, h) -> System.out.printf("%-2s -> %s%n", d, h));
    }

    private static void proximitySearch() {
        System.out.println("\n=== 4. Proximity search (radius 5 km around Notre-Dame) ===");
        double qLat = 48.8530, qLon = 2.3499;
        double radius = 5_000;
        int precision = GeoIndex.precisionForRadius(radius, qLat);
        System.out.println("chosen geohash precision for 5 km radius: " + precision);

        GeoIndex index = new GeoIndex(precision);
        List.of(
                new GeoIndex.Place("Louvre",         48.8606,  2.3376),
                new GeoIndex.Place("Eiffel Tower",   48.8584,  2.2945),
                new GeoIndex.Place("Sacre-Coeur",    48.8867,  2.3431),
                new GeoIndex.Place("La Defense",     48.8920,  2.2362),
                new GeoIndex.Place("Versailles",     48.8049,  2.1204),
                new GeoIndex.Place("Tokyo Tower",    35.6586, 139.7454)
        ).forEach(index::add);

        for (GeoIndex.Hit hit : index.nearby(qLat, qLon, radius)) {
            System.out.printf("%-14s %,8.0f m%n", hit.place().id(), hit.distanceMeters());
        }
    }
}
