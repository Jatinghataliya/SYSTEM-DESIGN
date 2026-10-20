package com.example.hyperloglog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HyperLogLogTest {

    private static double relError(double estimate, double truth) {
        return Math.abs(estimate - truth) / truth;
    }

    // ------------------------------------------------------------ construction

    @Test
    void rejectsInvalidPrecision() {
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLog(3));
        assertThrows(IllegalArgumentException.class, () -> new HyperLogLog(19));
        new HyperLogLog(4);
        new HyperLogLog(18);
    }

    @Test
    void registerCountIsTwoToTheP() {
        assertEquals(16, new HyperLogLog(4).registerCount());
        assertEquals(16_384, new HyperLogLog(14).registerCount());
    }

    @Test
    void standardErrorMatchesFormula() {
        assertEquals(1.04 / Math.sqrt(16_384), HyperLogLog.standardError(14), 1e-12);
        assertEquals(0.0081, HyperLogLog.standardError(14), 0.0001);
    }

    // ------------------------------------------------------------ hashing helpers

    @Test
    void indexUsesTheFirstPBits() {
        long hash = 0b1011L << 60; // top 4 bits = 1011
        assertEquals(0b1011, HyperLogLog.indexOf(hash, 4));
        assertEquals(0b101, HyperLogLog.indexOf(hash, 3));
    }

    @Test
    void rhoIsLeadingZerosPlusOneOfTheRemainingBits() {
        int p = 4;
        // remaining 60 bits start with a 1 -> rho = 1
        assertEquals(1, HyperLogLog.rho(0x0800000000000000L, p));
        // remaining bits start with 001 -> rho = 3
        assertEquals(3, HyperLogLog.rho(0x0200000000000000L, p));
    }

    @Test
    void rhoIsCappedWhenRemainingBitsAreAllZero() {
        int p = 4;
        assertEquals(64 - p + 1, HyperLogLog.rho(0xF000000000000000L, p));
        assertEquals(64 - p + 1, HyperLogLog.rho(0L, p));
    }

    @Test
    void hashSpreadsBitsRoughlyUniformly() {
        int[] ones = new int[64];
        int samples = 20_000;
        for (int i = 0; i < samples; i++) {
            long h = Hash64.of((long) i); // sequential input is the hard case
            for (int b = 0; b < 64; b++) {
                if (((h >>> b) & 1L) == 1L) {
                    ones[b]++;
                }
            }
        }
        for (int b = 0; b < 64; b++) {
            double frac = ones[b] / (double) samples;
            assertTrue(frac > 0.47 && frac < 0.53, "bit " + b + " set fraction " + frac);
        }
    }

    // ------------------------------------------------------------ basic behaviour

    @Test
    void emptySketchEstimatesZero() {
        HyperLogLog hll = new HyperLogLog(10);
        assertEquals(0, hll.count());
        assertEquals(hll.registerCount(), hll.zeroRegisters());
    }

    @Test
    void registersOnlyGrow() {
        HyperLogLog hll = new HyperLogLog(8);
        int[] previous = new int[hll.registerCount()];
        for (int i = 0; i < 5_000; i++) {
            hll.add(i);
            for (int j = 0; j < previous.length; j++) {
                assertTrue(hll.register(j) >= previous[j]);
                previous[j] = hll.register(j);
            }
        }
    }

    @Test
    void duplicatesDoNotChangeRegisters() {
        HyperLogLog once = new HyperLogLog(12);
        HyperLogLog repeated = new HyperLogLog(12);
        for (int i = 0; i < 10_000; i++) {
            once.add("user-" + i);
        }
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < 10_000; i++) {
                repeated.add("user-" + i);
            }
        }
        assertTrue(once.sameRegistersAs(repeated));
        assertEquals(once.count(), repeated.count());
    }

    @Test
    void insertionOrderDoesNotMatter() {
        HyperLogLog forward = new HyperLogLog(12);
        HyperLogLog backward = new HyperLogLog(12);
        for (int i = 0; i < 20_000; i++) {
            forward.add(i);
        }
        for (int i = 19_999; i >= 0; i--) {
            backward.add(i);
        }
        assertTrue(forward.sameRegistersAs(backward));
    }

    @Test
    void stringAndLongElementsBothCount() {
        HyperLogLog hll = new HyperLogLog(12);
        for (int i = 0; i < 1_000; i++) {
            hll.add("s" + i);
            hll.add((long) i);
        }
        assertTrue(relError(hll.estimate(), 2_000) < 0.05);
    }

    // ------------------------------------------------------------ accuracy

    @Test
    void smallCountsAreNearlyExact() {
        HyperLogLog hll = new HyperLogLog(14);
        for (int i = 0; i < 100; i++) {
            hll.add(i);
        }
        assertEquals(100, hll.count(), 3);
    }

    @Test
    void accuracyStaysWithinThreeSigmaAcrossScales() {
        int p = 14;
        double bound = 3 * HyperLogLog.standardError(p);
        HyperLogLog hll = new HyperLogLog(p);
        long added = 0;
        for (long target : new long[] {1_000, 10_000, 100_000, 1_000_000, 5_000_000}) {
            for (; added < target; added++) {
                hll.add(added);
            }
            assertTrue(relError(hll.estimate(), target) < bound,
                    "n=" + target + " estimate=" + hll.estimate());
        }
    }

    @Test
    void errorMatchesTheoryOverManyRuns() {
        int p = 10;
        int runs = 40;
        int n = 50_000;
        double sumSq = 0;
        for (int r = 0; r < runs; r++) {
            HyperLogLog hll = new HyperLogLog(p);
            long base = (long) r * 1_000_000L;
            for (int i = 0; i < n; i++) {
                hll.add(base + i);
            }
            double e = relError(hll.estimate(), n);
            sumSq += e * e;
        }
        double rms = Math.sqrt(sumSq / runs);
        double theory = HyperLogLog.standardError(p);
        assertTrue(rms > theory * 0.5 && rms < theory * 1.5,
                "rms=" + rms + " theory=" + theory);
    }

    @Test
    void morePrecisionGivesLowerError() {
        int n = 200_000;
        double errLow = meanError(8, n, 20);
        double errHigh = meanError(14, n, 20);
        assertTrue(errHigh < errLow, "p=8 error " + errLow + ", p=14 error " + errHigh);
    }

    private static double meanError(int p, int n, int runs) {
        double sum = 0;
        for (int r = 0; r < runs; r++) {
            HyperLogLog hll = new HyperLogLog(p);
            long base = (long) r * 10_000_000L;
            for (int i = 0; i < n; i++) {
                hll.add(base + i);
            }
            sum += relError(hll.estimate(), n);
        }
        return sum / runs;
    }

    @Test
    void memoryDoesNotGrowWithCardinality() {
        HyperLogLog small = new HyperLogLog(14);
        HyperLogLog large = new HyperLogLog(14);
        small.add(1);
        for (int i = 0; i < 1_000_000; i++) {
            large.add(i);
        }
        assertEquals(small.memoryBytes(), large.memoryBytes());
    }

    // ------------------------------------------------------------ merge

    @Test
    void mergeEqualsSketchOfTheUnion() {
        HyperLogLog a = new HyperLogLog(12);
        HyperLogLog b = new HyperLogLog(12);
        HyperLogLog both = new HyperLogLog(12);
        for (int i = 0; i < 30_000; i++) {
            a.add(i);
            both.add(i);
        }
        for (int i = 20_000; i < 60_000; i++) {
            b.add(i);
            both.add(i);
        }
        a.merge(b);
        assertTrue(a.sameRegistersAs(both));
    }

    @Test
    void mergeIsCommutativeAssociativeAndIdempotent() {
        HyperLogLog a = filled(12, 0, 10_000);
        HyperLogLog b = filled(12, 5_000, 20_000);
        HyperLogLog c = filled(12, 15_000, 30_000);

        assertTrue(HyperLogLog.union(a, b).sameRegistersAs(HyperLogLog.union(b, a)));
        assertTrue(HyperLogLog.union(HyperLogLog.union(a, b), c)
                .sameRegistersAs(HyperLogLog.union(a, HyperLogLog.union(b, c))));
        HyperLogLog twice = HyperLogLog.union(a, b);
        twice.merge(b);
        assertTrue(twice.sameRegistersAs(HyperLogLog.union(a, b)));
    }

    @Test
    void unionDoesNotModifyItsInputs() {
        HyperLogLog a = filled(10, 0, 1_000);
        HyperLogLog b = filled(10, 1_000, 2_000);
        HyperLogLog aCopy = a.copy();
        HyperLogLog bCopy = b.copy();
        HyperLogLog.union(a, b);
        assertTrue(a.sameRegistersAs(aCopy));
        assertTrue(b.sameRegistersAs(bCopy));
    }

    @Test
    void mergedOverlappingShardsCountEachElementOnce() {
        Random random = new Random(1);
        int shards = 6;
        HyperLogLog[] sketches = new HyperLogLog[shards];
        Set<Integer> exact = new HashSet<>();
        long sum = 0;
        for (int s = 0; s < shards; s++) {
            sketches[s] = new HyperLogLog(14);
            for (int i = 0; i < 100_000; i++) {
                int user = random.nextInt(150_000);
                sketches[s].add(user);
                exact.add(user);
            }
            sum += sketches[s].count();
        }
        HyperLogLog merged = HyperLogLog.union(sketches[0],
                java.util.Arrays.copyOfRange(sketches, 1, shards));
        assertTrue(relError(merged.estimate(), exact.size()) < 3 * HyperLogLog.standardError(14));
        assertTrue(sum > exact.size() * 1.5, "summing shard counts should badly overcount");
    }

    @Test
    void mergeRejectsDifferentPrecision() {
        assertThrows(IllegalArgumentException.class,
                () -> new HyperLogLog(10).merge(new HyperLogLog(11)));
    }

    @Test
    void mergeWithEmptyChangesNothing() {
        HyperLogLog a = filled(12, 0, 5_000);
        HyperLogLog before = a.copy();
        a.merge(new HyperLogLog(12));
        assertTrue(a.sameRegistersAs(before));
    }

    @Test
    void copyIsIndependent() {
        HyperLogLog a = filled(10, 0, 1_000);
        HyperLogLog c = a.copy();
        assertTrue(a.sameRegistersAs(c));
        for (int i = 1_000; i < 5_000; i++) {
            c.add(i);
        }
        assertFalse(a.sameRegistersAs(c));
    }

    private static HyperLogLog filled(int p, int fromInclusive, int toExclusive) {
        HyperLogLog hll = new HyperLogLog(p);
        for (int i = fromInclusive; i < toExclusive; i++) {
            hll.add(i);
        }
        return hll;
    }
}
