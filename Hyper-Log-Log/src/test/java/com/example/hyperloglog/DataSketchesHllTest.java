package com.example.hyperloglog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.hll.TgtHllType;
import org.apache.datasketches.hll.Union;
import org.junit.jupiter.api.Test;

/** Checks the Apache DataSketches HLL behaviour that {@link DataSketchesDemo} relies on. */
class DataSketchesHllTest {

    private static final int LG_K = 14;

    private static double relError(double estimate, double truth) {
        return Math.abs(estimate - truth) / truth;
    }

    private static HllSketch filled(int lgK, long fromInclusive, long toExclusive) {
        HllSketch s = new HllSketch(lgK);
        for (long i = fromInclusive; i < toExclusive; i++) {
            s.update(i);
        }
        return s;
    }

    @Test
    void emptySketchIsEmptyWithZeroEstimate() {
        HllSketch s = new HllSketch(LG_K);
        assertTrue(s.isEmpty());
        assertEquals(0.0, s.getEstimate(), 0.0);
    }

    @Test
    void lgKIsTheSameAsOurPrecision() {
        assertEquals(LG_K, new HllSketch(LG_K).getLgConfigK());
    }

    @Test
    void smallCountsAreExact() {
        // Below a threshold the sketch keeps the items themselves, not registers.
        assertEquals(1_000.0, filled(LG_K, 0, 1_000).getEstimate(), 0.0);
    }

    @Test
    void duplicatesDoNotChangeTheEstimate() {
        HllSketch once = filled(LG_K, 0, 50_000);
        HllSketch repeated = filled(LG_K, 0, 50_000);
        for (int round = 0; round < 5; round++) {
            for (long i = 0; i < 50_000; i++) {
                repeated.update(i);
            }
        }
        assertEquals(once.getEstimate(), repeated.getEstimate(), 0.0);
    }

    @Test
    void accuracyStaysWithinThreeSigmaAcrossScales() {
        double bound = 3 * HyperLogLog.standardError(LG_K);
        HllSketch s = new HllSketch(LG_K);
        long added = 0;
        for (long target : new long[] {1_000, 10_000, 100_000, 1_000_000, 5_000_000}) {
            for (; added < target; added++) {
                s.update(added);
            }
            assertTrue(relError(s.getEstimate(), target) < bound,
                    "n=" + target + " estimate=" + s.getEstimate());
        }
    }

    @Test
    void boundsBracketTheTruthAndWidenWithConfidence() {
        long n = 200_000;
        HllSketch s = filled(LG_K, 0, n);
        assertTrue(s.getLowerBound(3) <= n && n <= s.getUpperBound(3));
        assertTrue(s.getLowerBound(3) < s.getLowerBound(1));
        assertTrue(s.getUpperBound(3) > s.getUpperBound(1));
        assertTrue(s.getLowerBound(1) <= s.getEstimate() && s.getEstimate() <= s.getUpperBound(1));
    }

    @Test
    void registerTypesTradeSizeForSpeedButAgreeOnTheCount() {
        long n = 1_000_000;
        HllSketch h4 = new HllSketch(LG_K, TgtHllType.HLL_4);
        HllSketch h6 = new HllSketch(LG_K, TgtHllType.HLL_6);
        HllSketch h8 = new HllSketch(LG_K, TgtHllType.HLL_8);
        for (long i = 0; i < n; i++) {
            h4.update(i);
            h6.update(i);
            h8.update(i);
        }
        assertEquals(h4.getEstimate(), h6.getEstimate(), 0.0);
        assertEquals(h6.getEstimate(), h8.getEstimate(), 0.0);
        assertTrue(h4.getCompactSerializationBytes() < h6.getCompactSerializationBytes());
        assertTrue(h6.getCompactSerializationBytes() < h8.getCompactSerializationBytes());
        // Even HLL_8 is about the size of our byte[] (16,384 registers plus a small header).
        assertTrue(h8.getCompactSerializationBytes() < 16_384 + 100);
    }

    @Test
    void tinySketchIsFarSmallerThanAFullOne() {
        HllSketch tiny = filled(LG_K, 0, 1);
        HllSketch full = filled(LG_K, 0, 1_000_000);
        assertTrue(tiny.getCompactSerializationBytes() < 100);
        assertTrue(full.getCompactSerializationBytes() > 8_000);
    }

    @Test
    void serializationRoundTripPreservesTheEstimate() {
        HllSketch s = filled(LG_K, 0, 300_000);
        HllSketch back = HllSketch.heapify(s.toCompactByteArray());
        assertEquals(s.getEstimate(), back.getEstimate(), 0.0);
        assertEquals(s.getLgConfigK(), back.getLgConfigK());
    }

    @Test
    void unionOfOverlappingSketchesCountsEachElementOnce() {
        Union union = new Union(LG_K);
        union.update(filled(LG_K, 0, 60_000));
        union.update(filled(LG_K, 40_000, 100_000));
        assertTrue(relError(union.getResult().getEstimate(), 100_000) < 3 * HyperLogLog.standardError(LG_K));
    }

    @Test
    void unionOverShippedBytesBeatsSummingShardCounts() {
        Random random = new Random(1);
        int shards = 6;
        Union union = new Union(LG_K);
        java.util.Set<Integer> exact = new java.util.HashSet<>();
        double sum = 0;
        for (int s = 0; s < shards; s++) {
            HllSketch local = new HllSketch(LG_K);
            for (int i = 0; i < 100_000; i++) {
                int user = random.nextInt(150_000);
                local.update(user);
                exact.add(user);
            }
            sum += local.getEstimate();
            union.update(HllSketch.heapify(local.toCompactByteArray()));
        }
        assertTrue(relError(union.getResult().getEstimate(), exact.size()) < 3 * HyperLogLog.standardError(LG_K));
        assertTrue(sum > exact.size() * 1.5, "summing shard counts should badly overcount");
    }

    @Test
    void unionAcceptsDifferentLgKAndTakesTheSmallerAccuracy() {
        HllSketch big = filled(16, 0, 300_000);
        HllSketch small = filled(10, 200_000, 500_000);
        Union union = new Union(16);
        union.update(big);
        union.update(small);
        HllSketch result = union.getResult();
        assertEquals(10, result.getLgConfigK());
        assertTrue(relError(result.getEstimate(), 500_000) < 3 * HyperLogLog.standardError(10));
    }

    @Test
    void oursAndDataSketchesAgreeWithinTheirCombinedError() {
        long n = 500_000;
        HyperLogLog ours = new HyperLogLog(LG_K);
        HllSketch theirs = new HllSketch(LG_K);
        for (long i = 0; i < n; i++) {
            ours.add(i);
            theirs.update(i);
        }
        assertTrue(relError(ours.estimate(), theirs.getEstimate()) < 6 * HyperLogLog.standardError(LG_K));
    }

    @Test
    void unionResultIsIndependentOfTheInputSketches() {
        HllSketch a = filled(LG_K, 0, 10_000);
        HllSketch b = filled(LG_K, 10_000, 20_000);
        double aBefore = a.getEstimate();
        Union union = new Union(LG_K);
        union.update(a);
        union.update(b);
        union.getResult();
        assertEquals(aBefore, a.getEstimate(), 0.0);
        assertFalse(union.isEmpty());
    }
}
