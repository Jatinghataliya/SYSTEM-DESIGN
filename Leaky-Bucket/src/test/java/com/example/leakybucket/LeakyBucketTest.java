package com.example.leakybucket;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LeakyBucket}.
 *
 * Time is controlled by overriding {@code now()} so tests run instantly
 * without real {@code Thread.sleep} calls.
 */
class LeakyBucketTest {

    // -----------------------------------------------------------------------
    // Helper: controllable-clock subclass
    // -----------------------------------------------------------------------

    /** LeakyBucket whose clock is driven manually. */
    static class TestableLeakyBucket extends LeakyBucket {
        private long fakeTimeMs;

        TestableLeakyBucket(int capacity, double leakRate, long startMs) {
            super(capacity, leakRate);
            this.fakeTimeMs = startMs;
        }

        /** Advance the virtual clock by {@code deltaMs} milliseconds. */
        void advanceTime(long deltaMs) { fakeTimeMs += deltaMs; }

        @Override
        protected long now() { return fakeTimeMs; }
    }

    // -----------------------------------------------------------------------
    // Constructor validation
    // -----------------------------------------------------------------------

    @Test
    void constructor_rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket(0, 1.0));
    }

    @Test
    void constructor_rejectsNegativeLeakRate() {
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket(5, -1.0));
    }

    @Test
    void constructor_rejectsZeroLeakRate() {
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket(5, 0.0));
    }

    // -----------------------------------------------------------------------
    // Acceptance tests
    // -----------------------------------------------------------------------

    @Test
    void accepts_requests_up_to_capacity() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(5, 1.0, 0);
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume(), "request " + (i + 1) + " should be accepted");
        }
        assertEquals(5, bucket.getTotalAccepted());
        assertEquals(0, bucket.getTotalDropped());
    }

    @Test
    void drops_requests_when_bucket_full() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(5, 1.0, 0);
        for (int i = 0; i < 5; i++) bucket.tryConsume();   // fill the bucket

        boolean result = bucket.tryConsume();               // 6th should be dropped
        assertFalse(result);
        assertEquals(1, bucket.getTotalDropped());
    }

    @Test
    void accepts_single_request_into_empty_bucket() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(10, 5.0, 0);
        assertTrue(bucket.tryConsume());
    }

    // -----------------------------------------------------------------------
    // Leak / time-based tests
    // -----------------------------------------------------------------------

    @Test
    void water_level_decreases_after_time_passes() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(10, 2.0, 0);
        // Fill bucket to 5
        for (int i = 0; i < 5; i++) bucket.tryConsume();
        assertEquals(5.0, bucket.getCurrentWater(), 0.001);

        // Advance 1 second → should leak 2 requests
        bucket.advanceTime(1000);
        assertEquals(3.0, bucket.getCurrentWater(), 0.001);
    }

    @Test
    void water_level_does_not_go_below_zero() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(10, 5.0, 0);
        bucket.tryConsume(); // water = 1

        // Advance 10 seconds → should leak all water, not go negative
        bucket.advanceTime(10_000);
        assertEquals(0.0, bucket.getCurrentWater(), 0.001);
    }

    @Test
    void bucket_accepts_again_after_leaking() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(3, 3.0, 0);
        // Fill to capacity
        for (int i = 0; i < 3; i++) assertTrue(bucket.tryConsume());
        assertFalse(bucket.tryConsume()); // full

        // Advance 1 second → 3 requests leak out; bucket is empty
        bucket.advanceTime(1000);
        assertTrue(bucket.tryConsume()); // should now be accepted
    }

    @Test
    void partial_leak_allows_partial_refill() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(4, 2.0, 0);
        // Fill to capacity
        for (int i = 0; i < 4; i++) bucket.tryConsume();
        assertFalse(bucket.tryConsume()); // full

        // Advance 500 ms → leaks 1 request (2/s * 0.5s)
        bucket.advanceTime(500);
        assertTrue(bucket.tryConsume());  // 1 slot freed
        assertFalse(bucket.tryConsume()); // still full
    }

    // -----------------------------------------------------------------------
    // Stats tracking
    // -----------------------------------------------------------------------

    @Test
    void stats_track_accepted_and_dropped_correctly() {
        TestableLeakyBucket bucket = new TestableLeakyBucket(3, 1.0, 0);
        bucket.tryConsume(); // accepted
        bucket.tryConsume(); // accepted
        bucket.tryConsume(); // accepted
        bucket.tryConsume(); // dropped
        bucket.tryConsume(); // dropped

        assertEquals(3, bucket.getTotalAccepted());
        assertEquals(2, bucket.getTotalDropped());
    }

    // -----------------------------------------------------------------------
    // Metadata accessors
    // -----------------------------------------------------------------------

    @Test
    void getCapacity_returns_configured_capacity() {
        LeakyBucket bucket = new LeakyBucket(7, 2.0);
        assertEquals(7, bucket.getCapacity());
    }

    @Test
    void getLeakRate_returns_configured_rate() {
        LeakyBucket bucket = new LeakyBucket(7, 2.5);
        assertEquals(2.5, bucket.getLeakRatePerSecond(), 0.0001);
    }

    @Test
    void toString_contains_key_fields() {
        LeakyBucket bucket = new LeakyBucket(10, 5.0);
        String s = bucket.toString();
        assertTrue(s.contains("capacity=10"));
        assertTrue(s.contains("leakRate=5.00/s"));
    }
}
