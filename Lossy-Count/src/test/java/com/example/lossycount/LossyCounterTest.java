package com.example.lossycount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LossyCounterTest {

    private static final int N = 200_000;
    private static final int UNIVERSE = 20_000;

    private static int[] zipf(long seed) {
        ZipfStream s = new ZipfStream(UNIVERSE, 1.1, seed);
        int[] data = new int[N];
        for (int i = 0; i < N; i++) {
            data[i] = s.next();
        }
        return data;
    }

    // ---------------------------------------------------------------- basics

    @Test
    void bucketWidthIsCeilingOfOneOverEpsilon() {
        assertEquals(4, new LossyCounter<String>(0.25).bucketWidth());
        assertEquals(1000, new LossyCounter<String>(0.001).bucketWidth());
        assertEquals(4, new LossyCounter<String>(0.3).bucketWidth()); // 1/0.3 = 3.33, rounded up
    }

    @Test
    void rejectsInvalidEpsilon() {
        assertThrows(IllegalArgumentException.class, () -> new LossyCounter<String>(0));
        assertThrows(IllegalArgumentException.class, () -> new LossyCounter<String>(1));
        assertThrows(IllegalArgumentException.class, () -> new LossyCounter<String>(-0.1));
    }

    @Test
    void rejectsSupportNotAboveEpsilon() {
        LossyCounter<String> c = new LossyCounter<>(0.1);
        assertThrows(IllegalArgumentException.class, () -> c.frequentItems(0.1));
        assertThrows(IllegalArgumentException.class, () -> c.frequentItems(0.05));
        assertThrows(IllegalArgumentException.class, () -> c.frequentItems(1.5));
    }

    @Test
    void countsExactlyWhenNothingIsPruned() {
        LossyCounter<String> c = new LossyCounter<>(0.01); // width 100
        for (int i = 0; i < 10; i++) {
            c.add("x");
        }
        for (int i = 0; i < 3; i++) {
            c.add("y");
        }
        assertEquals(10, c.estimate("x"));
        assertEquals(3, c.estimate("y"));
        assertEquals(0, c.estimate("never-seen"));
        assertEquals(13, c.itemsSeen());
    }

    // ---------------------------------------------------------------- trace from the README

    @Test
    void matchesWorkedExampleInReadme() {
        LossyCounter<String> c = new LossyCounter<>(0.25); // width 4
        for (String s : new String[] {"a", "a", "b", "c"}) {
            c.add(s);
        }
        // bucket 1 ended: b(1,0) and c(1,0) have f+delta = 1 <= 1 and are dropped
        assertEquals(1, c.size());
        assertEquals(2, c.estimate("a"));

        c.add("a");
        c.add("d"); // inserted with delta = 1
        LossyCounter.Entry<String> d = c.entries().stream()
                .filter(e -> e.item().equals("d")).findFirst().orElseThrow();
        assertEquals(1, d.count());
        assertEquals(1, d.delta());
        c.add("a");
        c.add("b"); // bucket 2 ended: d(1,1) and b(1,1) have f+delta = 2 <= 2 and are dropped

        assertEquals(1, c.size());
        assertEquals(4, c.estimate("a"));
        assertEquals(0, c.estimate("b"));
        assertEquals(0, c.estimate("d"));
    }

    @Test
    void newEntryDeltaIsCurrentBucketMinusOne() {
        LossyCounter<Integer> c = new LossyCounter<>(0.1); // width 10
        // 25 distinct items: the 25th arrives in bucket 3
        for (int i = 0; i < 25; i++) {
            c.add(i);
        }
        assertEquals(3, c.currentBucket());
        LossyCounter.Entry<Integer> last = c.entries().stream()
                .filter(e -> e.item() == 24).findFirst().orElseThrow();
        assertEquals(2, last.delta());
    }

    @Test
    void currentBucketAdvancesEveryWidthItems() {
        LossyCounter<Integer> c = new LossyCounter<>(0.1); // width 10
        for (int i = 1; i <= 30; i++) {
            c.add(i);
            assertEquals((i + 9) / 10, c.currentBucket());
        }
    }

    // ---------------------------------------------------------------- pruning

    @Test
    void frequentItemSurvivesEveryPrune() {
        LossyCounter<String> c = new LossyCounter<>(0.01); // width 100
        for (int i = 0; i < 5_000; i++) {
            c.add("hot");
            c.add("noise-" + i); // all distinct
        }
        assertTrue(c.estimate("hot") > 0);
        assertEquals(5_000, c.estimate("hot")); // inserted first, never pruned, so exact
    }

    @Test
    void rareItemsArePruned() {
        LossyCounter<Integer> c = new LossyCounter<>(0.01); // width 100
        for (int i = 0; i < 10_000; i++) {
            c.add(i); // every item distinct
        }
        assertTrue(c.size() <= c.bucketWidth(),
                "all-distinct stream should leave at most one bucket of entries, got " + c.size());
    }

    @Test
    void memoryStaysBelowTheoreticalBound() {
        double eps = 0.001;
        LossyCounter<Integer> c = new LossyCounter<>(eps);
        Random r = new Random(7);
        for (int i = 0; i < 500_000; i++) {
            c.add(r.nextInt(1_000_000));
        }
        double bound = (1 / eps) * Math.log(eps * 500_000);
        assertTrue(c.peakSize() <= bound, "peak " + c.peakSize() + " exceeds bound " + bound);
    }

    // ---------------------------------------------------------------- guarantees

    @Test
    void neverOvercounts() {
        int[] data = zipf(1);
        LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
        ExactCounter<Integer> exact = new ExactCounter<>();
        for (int item : data) {
            lossy.add(item);
            exact.add(item);
        }
        for (LossyCounter.Entry<Integer> e : lossy.entries()) {
            assertTrue(e.count() <= exact.count(e.item()),
                    "overcounted " + e.item() + ": " + e.count() + " > " + exact.count(e.item()));
        }
    }

    @Test
    void undercountIsAtMostEpsilonTimesN() {
        int[] data = zipf(2);
        LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
        ExactCounter<Integer> exact = new ExactCounter<>();
        for (int item : data) {
            lossy.add(item);
            exact.add(item);
        }
        for (Map.Entry<Integer, Long> t : exact.counts().entrySet()) {
            long error = t.getValue() - lossy.estimate(t.getKey());
            assertTrue(error <= lossy.maxError(),
                    "item " + t.getKey() + " off by " + error + " > " + lossy.maxError());
        }
    }

    @Test
    void estimatePlusDeltaNeverBelowTrueCount() {
        int[] data = zipf(3);
        LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
        ExactCounter<Integer> exact = new ExactCounter<>();
        for (int item : data) {
            lossy.add(item);
            exact.add(item);
        }
        for (LossyCounter.Entry<Integer> e : lossy.entries()) {
            assertTrue(e.count() + e.delta() >= exact.count(e.item()));
        }
    }

    @Test
    void noFalseNegatives() {
        for (long seed = 10; seed < 15; seed++) {
            int[] data = zipf(seed);
            double support = 0.01;
            LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
            ExactCounter<Integer> exact = new ExactCounter<>();
            for (int item : data) {
                lossy.add(item);
                exact.add(item);
            }
            Set<Integer> reported = new HashSet<>();
            for (LossyCounter.Entry<Integer> e : lossy.frequentItems(support)) {
                reported.add(e.item());
            }
            for (Map.Entry<Integer, Long> t : exact.counts().entrySet()) {
                if (t.getValue() >= support * N) {
                    assertTrue(reported.contains(t.getKey()),
                            "missed frequent item " + t.getKey() + " (seed " + seed + ")");
                }
            }
        }
    }

    @Test
    void noReportedItemIsBelowSupportMinusEpsilon() {
        int[] data = zipf(20);
        double support = 0.01;
        double eps = 0.001;
        LossyCounter<Integer> lossy = new LossyCounter<>(eps);
        ExactCounter<Integer> exact = new ExactCounter<>();
        for (int item : data) {
            lossy.add(item);
            exact.add(item);
        }
        for (LossyCounter.Entry<Integer> e : lossy.frequentItems(support)) {
            assertTrue(exact.count(e.item()) >= (support - eps) * N,
                    "item " + e.item() + " is too rare to be reported");
        }
    }

    @Test
    void frequentItemsAreSortedMostFrequentFirst() {
        LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
        for (int item : zipf(30)) {
            lossy.add(item);
        }
        List<LossyCounter.Entry<Integer>> top = lossy.frequentItems(0.01);
        assertFalse(top.isEmpty());
        for (int i = 1; i < top.size(); i++) {
            assertTrue(top.get(i - 1).count() >= top.get(i).count());
        }
    }

    // ---------------------------------------------------------------- memory vs exact

    @Test
    void usesFarFewerCountersThanExactOnSkewedData() {
        int[] data = zipf(40);
        LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
        ExactCounter<Integer> exact = new ExactCounter<>();
        for (int item : data) {
            lossy.add(item);
            exact.add(item);
        }
        assertTrue(lossy.peakSize() < exact.size(),
                "lossy peak " + lossy.peakSize() + " vs exact " + exact.size());
    }

    @Test
    void zipfStreamIsReproducibleAndSkewed() {
        ZipfStream a = new ZipfStream(1000, 1.1, 99);
        ZipfStream b = new ZipfStream(1000, 1.1, 99);
        int ones = 0;
        for (int i = 0; i < 10_000; i++) {
            int x = a.next();
            assertEquals(x, b.next());
            assertTrue(x >= 1 && x <= 1000);
            if (x == 1) {
                ones++;
            }
        }
        assertTrue(ones > 1_000, "item 1 should dominate, saw " + ones);
    }
}
