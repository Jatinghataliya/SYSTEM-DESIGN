package com.example.lossycount;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Demo of Lossy Counting. Run with: mvn compile exec:java */
public class Main {

    private static final int STREAM_SIZE = 1_000_000;
    private static final int UNIVERSE = 100_000;
    private static final double ZIPF_EXPONENT = 1.1;
    private static final long SEED = 42;

    public static void main(String[] args) {
        section1TraceSmallExample();
        section2AccuracyAndMemory();
        section3EffectOfEpsilon();
        section4TableSizeOverTime();
        section5UniformStream();
    }

    // ------------------------------------------------------------------ 1
    private static void section1TraceSmallExample() {
        header("1. Trace on a tiny stream (epsilon = 0.25, bucket width = 4)");
        LossyCounter<String> counter = new LossyCounter<>(0.25);
        String[] stream = {"a", "a", "b", "c", "a", "d", "a", "b"};
        for (String item : stream) {
            counter.add(item);
            boolean pruned = counter.itemsSeen() % counter.bucketWidth() == 0;
            System.out.printf("  N=%d bucket=%d add %-2s -> table %s%s%n",
                    counter.itemsSeen(), counter.currentBucket(), item,
                    sorted(counter.entries()), pruned ? "   (bucket ended, pruned)" : "");
        }
        System.out.println("  Final: " + sorted(counter.entries()) + "   true count of a = 4");
    }

    // ------------------------------------------------------------------ 2
    private static void section2AccuracyAndMemory() {
        header("2. Accuracy and memory on a skewed stream");
        double support = 0.01;
        double epsilon = 0.001;
        System.out.printf("  Stream: %,d items, %,d possible ids, Zipf exponent %.1f%n",
                STREAM_SIZE, UNIVERSE, ZIPF_EXPONENT);
        System.out.printf("  support s = %.1f%%, epsilon = %.2f%%, bucket width = %,d%n%n",
                support * 100, epsilon * 100, (long) Math.ceil(1 / epsilon));

        LossyCounter<Integer> lossy = new LossyCounter<>(epsilon);
        ExactCounter<Integer> exact = new ExactCounter<>();
        ZipfStream stream = new ZipfStream(UNIVERSE, ZIPF_EXPONENT, SEED);
        for (int i = 0; i < STREAM_SIZE; i++) {
            int item = stream.next();
            lossy.add(item);
            exact.add(item);
        }

        System.out.printf("  Exact counter:  %,d counters held (one per distinct item)%n", exact.size());
        System.out.printf("  Lossy counter:  %,d counters held at the end, %,d at the peak%n",
                lossy.size(), lossy.peakSize());
        System.out.printf("  Memory saved:   %.1fx fewer counters at the peak%n%n",
                (double) exact.size() / lossy.peakSize());

        double trueCut = support * STREAM_SIZE;
        double lowCut = (support - epsilon) * STREAM_SIZE;
        List<LossyCounter.Entry<Integer>> reported = lossy.frequentItems(support);

        Set<Integer> trulyFrequent = new HashSet<>();
        for (Map.Entry<Integer, Long> e : exact.counts().entrySet()) {
            if (e.getValue() >= trueCut) {
                trulyFrequent.add(e.getKey());
            }
        }
        Set<Integer> reportedIds = new HashSet<>();
        long maxUnder = 0;
        long overcounts = 0;
        int falsePositives = 0;
        int borderline = 0;
        for (LossyCounter.Entry<Integer> e : reported) {
            reportedIds.add(e.item());
            long truth = exact.count(e.item());
            maxUnder = Math.max(maxUnder, truth - e.count());
            if (e.count() > truth) {
                overcounts++;
            }
            if (!trulyFrequent.contains(e.item())) {
                falsePositives++;
                if (truth >= lowCut) {
                    borderline++;
                }
            }
        }
        int falseNegatives = 0;
        for (int id : trulyFrequent) {
            if (!reportedIds.contains(id)) {
                falseNegatives++;
            }
        }

        System.out.printf("  Truly frequent (true count >= %,.0f): %d items%n", trueCut, trulyFrequent.size());
        System.out.printf("  Reported by Lossy Counting:            %d items%n", reported.size());
        System.out.printf("  False negatives:                       %d   (guarantee: 0)%n", falseNegatives);
        System.out.printf("  False positives:                       %d   (all within the margin: %d of %d)%n",
                falsePositives, borderline, falsePositives);
        System.out.printf("  Estimates above the true count:        %d   (guarantee: 0)%n", overcounts);
        System.out.printf("  Worst undercount:                      %d   (guarantee: <= %,.0f)%n%n",
                maxUnder, lossy.maxError());

        System.out.println("  Top 10:");
        System.out.printf("  %-6s %-10s %-10s %-8s%n", "Item", "Estimate", "True", "Error");
        int shown = 0;
        for (LossyCounter.Entry<Integer> e : reported) {
            if (shown++ == 10) {
                break;
            }
            long truth = exact.count(e.item());
            System.out.printf("  %-6d %-10d %-10d %-8d%n", e.item(), e.count(), truth, truth - e.count());
        }
    }

    // ------------------------------------------------------------------ 3
    private static void section3EffectOfEpsilon() {
        header("3. Effect of epsilon (same stream, support = 1%)");
        double support = 0.01;
        double[] epsilons = {0.005, 0.002, 0.001, 0.0005, 0.0002};

        ExactCounter<Integer> exact = new ExactCounter<>();
        int[] data = new int[STREAM_SIZE];
        ZipfStream stream = new ZipfStream(UNIVERSE, ZIPF_EXPONENT, SEED);
        for (int i = 0; i < STREAM_SIZE; i++) {
            data[i] = stream.next();
            exact.add(data[i]);
        }
        double trueCut = support * STREAM_SIZE;
        Set<Integer> trulyFrequent = new HashSet<>();
        for (Map.Entry<Integer, Long> e : exact.counts().entrySet()) {
            if (e.getValue() >= trueCut) {
                trulyFrequent.add(e.getKey());
            }
        }

        System.out.printf("  Truly frequent items: %d. Exact counter holds %,d counters.%n%n",
                trulyFrequent.size(), exact.size());
        System.out.printf("  %-9s %-8s %-11s %-10s %-11s %-9s %-12s%n",
                "epsilon", "width", "peak size", "reported", "false pos.", "false neg.", "max error");
        for (double eps : epsilons) {
            LossyCounter<Integer> lossy = new LossyCounter<>(eps);
            for (int item : data) {
                lossy.add(item);
            }
            List<LossyCounter.Entry<Integer>> reported = lossy.frequentItems(support);
            Set<Integer> ids = new HashSet<>();
            long maxUnder = 0;
            int fp = 0;
            for (LossyCounter.Entry<Integer> e : reported) {
                ids.add(e.item());
                maxUnder = Math.max(maxUnder, exact.count(e.item()) - e.count());
                if (!trulyFrequent.contains(e.item())) {
                    fp++;
                }
            }
            int fn = 0;
            for (int id : trulyFrequent) {
                if (!ids.contains(id)) {
                    fn++;
                }
            }
            System.out.printf("  %-9s %-8d %-11d %-10d %-11d %-9d %-12d%n",
                    pct(eps), lossy.bucketWidth(), lossy.peakSize(), reported.size(), fp, fn, maxUnder);
        }
        System.out.println();
        System.out.println("  Smaller epsilon: more memory, fewer false positives, smaller error.");
    }

    // ------------------------------------------------------------------ 4
    private static void section4TableSizeOverTime() {
        header("4. Table size over time (epsilon = 0.1%)");
        LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
        ExactCounter<Integer> exact = new ExactCounter<>();
        ZipfStream stream = new ZipfStream(UNIVERSE, ZIPF_EXPONENT, SEED);

        System.out.printf("  %-12s %-16s %-16s %-14s%n",
                "Items seen", "Exact counters", "Lossy counters", "Max error");
        for (int i = 1; i <= STREAM_SIZE; i++) {
            int item = stream.next();
            lossy.add(item);
            exact.add(item);
            if (i % 100_000 == 0) {
                System.out.printf("  %-,12d %-,16d %-,16d %-,14.0f%n",
                        i, exact.size(), lossy.size(), lossy.maxError());
            }
        }
        System.out.println();
        System.out.println("  The exact table keeps growing with every new distinct item.");
        System.out.println("  The lossy table stays small. Its max error grows with N, as epsilon * N.");
    }

    // ------------------------------------------------------------------ 5
    private static void section5UniformStream() {
        header("5. When there is no skew (uniform stream, epsilon = 0.1%)");
        LossyCounter<Integer> lossy = new LossyCounter<>(0.001);
        ExactCounter<Integer> exact = new ExactCounter<>();
        java.util.Random random = new java.util.Random(SEED);
        for (int i = 0; i < STREAM_SIZE; i++) {
            int item = random.nextInt(UNIVERSE);
            lossy.add(item);
            exact.add(item);
        }
        System.out.printf("  Exact counters: %,d%n", exact.size());
        System.out.printf("  Lossy peak:     %,d   (bound: (1/e) * log(e * N) = %,.0f)%n",
                lossy.peakSize(), (1 / lossy.epsilon()) * Math.log(lossy.epsilon() * STREAM_SIZE));
        System.out.printf("  Reported at s=1%%: %d items (nothing is frequent)%n",
                lossy.frequentItems(0.01).size());
        System.out.println("  Every item is rare, so each bucket is pruned almost empty and the peak is");
        System.out.println("  about one bucket width, far below the theoretical bound. Nothing is frequent.");
    }

    // ------------------------------------------------------------------ helpers
    private static <T> String sorted(List<LossyCounter.Entry<T>> entries) {
        List<String> parts = new ArrayList<>();
        entries.sort((x, y) -> x.item().toString().compareTo(y.item().toString()));
        for (LossyCounter.Entry<T> e : entries) {
            parts.add(e.toString());
        }
        return parts.toString();
    }

    private static String pct(double v) {
        return String.format("%.2f%%", v * 100);
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
