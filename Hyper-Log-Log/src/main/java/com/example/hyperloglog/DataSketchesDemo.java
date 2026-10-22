package com.example.hyperloglog;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.apache.datasketches.hll.HllSketch;
import org.apache.datasketches.hll.TgtHllType;
import org.apache.datasketches.hll.Union;

/**
 * HyperLogLog with Apache DataSketches, compared with our hand-written {@link HyperLogLog}.
 *
 * <p>Run with:
 * {@code mvn compile exec:java -Dexec.mainClass=com.example.hyperloglog.DataSketchesDemo}
 *
 * <p>In DataSketches, {@code lgK} is the same thing as our precision {@code p}: the sketch has
 * {@code K = 2^lgK} registers.
 */
public class DataSketchesDemo {

    private static final int LG_K = 14;

    public static void main(String[] args) {
        section1BasicUsage();
        section2BoundsAndConfidence();
        section3OursVersusDataSketches();
        section4MemoryAndRegisterTypes();
        section5UnionAcrossShardsWithSerialization();
        section6UnionOfDifferentSizes();
    }

    // ------------------------------------------------------------------ 1
    private static void section1BasicUsage() {
        header("1. Basic usage (lgK = " + LG_K + ", HLL_4)");
        HllSketch sketch = new HllSketch(LG_K); // default type is HLL_4
        for (int i = 0; i < 1_000_000; i++) {
            sketch.update(i);
        }
        for (int i = 0; i < 1_000_000; i++) {
            sketch.update(i); // duplicates change nothing
        }
        System.out.printf("  Added 2,000,000 events, 1,000,000 distinct%n");
        System.out.printf("  getEstimate():   %,.0f   (%+.2f%%)%n",
                sketch.getEstimate(), pctError(sketch.getEstimate(), 1_000_000));
        System.out.printf("  getLgConfigK():  %d  (K = %,d registers)%n", sketch.getLgConfigK(), 1 << sketch.getLgConfigK());
        System.out.printf("  getTgtHllType(): %s%n", sketch.getTgtHllType());
        System.out.printf("  Strings work too: update(String), update(double), update(byte[]), update(long[]).%n");
    }

    // ------------------------------------------------------------------ 2
    private static void section2BoundsAndConfidence() {
        header("2. Error bounds come with the sketch (lgK = " + LG_K + ")");
        System.out.printf("  %-14s %-12s %-26s %-26s %-26s%n", "true distinct", "estimate",
                "1 std dev (~68%)", "2 std dev (~95%)", "3 std dev (~99.7%)");
        long[] sizes = {1_000, 100_000, 5_000_000};
        HllSketch sketch = new HllSketch(LG_K);
        long added = 0;
        for (long target : sizes) {
            for (; added < target; added++) {
                sketch.update(added);
            }
            System.out.printf("  %-,14d %-,12.0f %-26s %-26s %-26s%n", target, sketch.getEstimate(),
                    range(sketch, 1), range(sketch, 2), range(sketch, 3));
        }
        System.out.println();
        System.out.println("  Our class returns one number. DataSketches also tells you how sure it is,");
        System.out.println("  so a dashboard can show \"about 1.0M (0.99M to 1.01M)\".");
    }

    private static String range(HllSketch sketch, int stdDevs) {
        return String.format("%,.0f to %,.0f", sketch.getLowerBound(stdDevs), sketch.getUpperBound(stdDevs));
    }

    // ------------------------------------------------------------------ 3
    private static void section3OursVersusDataSketches() {
        header("3. Our HyperLogLog vs DataSketches HllSketch (lgK = p = " + LG_K + ", 20 runs per row)");
        System.out.printf("  Root-mean-square error over 20 independent runs. Same 2^%d registers each.%n%n", LG_K);
        System.out.printf("  %-14s %-14s %-16s%n", "true distinct", "ours (RMS)", "DataSketches (RMS)");
        int runs = 20;
        for (long n : new long[] {1_000, 20_000, 50_000, 100_000, 1_000_000}) {
            double oursSq = 0;
            double theirsSq = 0;
            for (int r = 0; r < runs; r++) {
                long base = (long) r * 100_000_000L;
                HyperLogLog ours = new HyperLogLog(LG_K);
                HllSketch theirs = new HllSketch(LG_K);
                for (long i = 0; i < n; i++) {
                    ours.add(base + i);
                    theirs.update(base + i);
                }
                double eo = pctError(ours.estimate(), n);
                double et = pctError(theirs.getEstimate(), n);
                oursSq += eo * eo;
                theirsSq += et * et;
            }
            System.out.printf("  %-,14d %-14s %-16s%n", n,
                    String.format("%.2f%%", Math.sqrt(oursSq / runs)),
                    String.format("%.2f%%", Math.sqrt(theirsSq / runs)));
        }
        System.out.println();
        System.out.printf("  Our theoretical error is 1.04/sqrt(m) = %.2f%%.%n", HyperLogLog.standardError(LG_K) * 100);
        System.out.println("  DataSketches is nearly exact for small counts (it stores hashed items before");
        System.out.println("  switching to registers) and uses the HIP estimator for a sketch built by updates.");
        System.out.println("  Only 20 runs per row, so compare the trend, not single cells.");
    }

    // ------------------------------------------------------------------ 4
    private static void section4MemoryAndRegisterTypes() {
        header("4. Memory and register types (lgK = " + LG_K + ")");
        System.out.printf("  Our class: %,d bytes for any cardinality (1 byte per register).%n%n",
                new HyperLogLog(LG_K).memoryBytes());
        System.out.printf("  %-8s %-14s %-22s %-22s%n", "type", "distinct", "compact bytes (stored)", "updatable bytes (live)");
        for (TgtHllType type : TgtHllType.values()) {
            for (int n : new int[] {1, 100, 1_000_000}) {
                HllSketch s = new HllSketch(LG_K, type);
                for (int i = 0; i < n; i++) {
                    s.update(i);
                }
                System.out.printf("  %-8s %-,14d %-,22d %-,22d%n", type, n,
                        s.getCompactSerializationBytes(), s.getUpdatableSerializationBytes());
            }
        }
        System.out.println();
        System.out.println("  HLL_4 packs a register into 4 bits (about 8 KB at lgK=14), HLL_6 into 6 bits (12 KB),");
        System.out.println("  HLL_8 into 8 bits (16 KB, fastest). Small sketches use a tiny list/set mode,");
        System.out.println("  so a sketch with 1 element is a few bytes, not 16 KB.");
    }

    // ------------------------------------------------------------------ 5
    private static void section5UnionAcrossShardsWithSerialization() {
        header("5. Union across 8 servers, shipping serialized bytes (lgK = " + LG_K + ")");
        int shards = 8;
        int totalUsers = 400_000;
        Random random = new Random(42);
        byte[][] wire = new byte[shards][];
        Set<Integer> exact = new HashSet<>();
        long sumOfShards = 0;
        long bytesShipped = 0;

        for (int s = 0; s < shards; s++) {
            HllSketch local = new HllSketch(LG_K);
            for (int i = 0; i < 200_000; i++) {
                int user = random.nextInt(totalUsers);
                local.update(user);
                exact.add(user);
            }
            sumOfShards += Math.round(local.getEstimate());
            wire[s] = local.toCompactByteArray(); // what a server would send over the network
            bytesShipped += wire[s].length;
        }

        Union union = new Union(LG_K);
        for (byte[] bytes : wire) {
            union.update(HllSketch.heapify(bytes)); // coordinator rebuilds each sketch and merges
        }
        double merged = union.getResult().getEstimate();

        System.out.printf("  True distinct users overall:       %,d%n", exact.size());
        System.out.printf("  Sum of per-server counts (WRONG):  %,d  (%+.1f%%)%n",
                sumOfShards, pctError(sumOfShards, exact.size()));
        System.out.printf("  Union of the 8 sketches:           %,.0f  (%+.2f%%)%n", merged, pctError(merged, exact.size()));
        System.out.printf("  Bytes shipped to the coordinator:  %,d  (%,d per server) vs %,d ids%n",
                bytesShipped, bytesShipped / shards, exact.size());
        System.out.println("  toCompactByteArray() and HllSketch.heapify(bytes) are the serialization round trip.");
        System.out.println("  The format is shared with the C++ and Python DataSketches libraries.");
    }

    // ------------------------------------------------------------------ 6
    private static void section6UnionOfDifferentSizes() {
        header("6. Union of sketches with different lgK");
        HllSketch big = new HllSketch(16);
        HllSketch small = new HllSketch(10);
        for (int i = 0; i < 300_000; i++) {
            big.update(i);
        }
        for (int i = 200_000; i < 500_000; i++) {
            small.update(i);
        }
        Union union = new Union(16);
        union.update(big);
        union.update(small);
        HllSketch result = union.getResult();
        System.out.printf("  lgK=16 sketch: %,.0f   (true 300,000)%n", big.getEstimate());
        System.out.printf("  lgK=10 sketch: %,.0f   (true 300,000)%n", small.getEstimate());
        System.out.printf("  Union result:  %,.0f   (true 500,000), result lgK = %d%n",
                result.getEstimate(), result.getLgConfigK());
        System.out.println("  Our class refuses to merge different precisions. DataSketches merges them and");
        System.out.println("  the result takes the accuracy of the smaller sketch.");
    }

    // ------------------------------------------------------------------ helpers
    private static double pctError(double estimate, double truth) {
        return (estimate - truth) / truth * 100;
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
