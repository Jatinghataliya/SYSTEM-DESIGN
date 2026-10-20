package com.example.leakybucket;

/**
 * Demonstration of the Leaky Bucket rate-limiter.
 *
 * Scenario A – Burst handling:
 *   Send 15 requests instantly into a bucket of capacity 10 at 5 req/s.
 *   Requests 11-15 should be dropped.
 *
 * Scenario B – Steady-state:
 *   Send one request every 300 ms into the same bucket.
 *   All should be accepted because the arrival rate (≈3.3/s) < leak rate (5/s).
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Leaky Bucket Rate Limiter Demo ===\n");

        scenarioA();
        System.out.println();
        scenarioB();
    }

    // -----------------------------------------------------------------------
    // Scenario A: burst of 15 requests into a bucket with capacity 10
    // -----------------------------------------------------------------------
    private static void scenarioA() {
        System.out.println("--- Scenario A: Burst of 15 requests (capacity=10, leak=5/s) ---");
        LeakyBucket bucket = new LeakyBucket(10, 5.0);

        for (int i = 1; i <= 15; i++) {
            boolean accepted = bucket.tryConsume();
            System.out.printf("  Request %2d → %s  (water=%.1f)%n",
                    i, accepted ? "ACCEPTED" : "DROPPED ", bucket.getCurrentWater());
        }

        System.out.printf("%nTotal accepted: %d | Total dropped: %d%n",
                bucket.getTotalAccepted(), bucket.getTotalDropped());
    }

    // -----------------------------------------------------------------------
    // Scenario B: steady arrival rate below the leak rate
    // -----------------------------------------------------------------------
    private static void scenarioB() throws InterruptedException {
        System.out.println("--- Scenario B: Steady rate ~3.3 req/s (capacity=10, leak=5/s) ---");
        LeakyBucket bucket = new LeakyBucket(10, 5.0);

        int accepted = 0, dropped = 0;
        for (int i = 1; i <= 10; i++) {
            boolean ok = bucket.tryConsume();
            if (ok) accepted++; else dropped++;
            System.out.printf("  Request %2d → %s  (water=%.1f)%n",
                    i, ok ? "ACCEPTED" : "DROPPED ", bucket.getCurrentWater());
            Thread.sleep(300); // ~3.3 req/s
        }

        System.out.printf("%nTotal accepted: %d | Total dropped: %d%n", accepted, dropped);
    }
}
