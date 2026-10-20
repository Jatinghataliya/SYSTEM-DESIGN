package com.example.hyperloglog;

import java.util.HashSet;
import java.util.Set;

/** Demo of HyperLogLog. Run with: mvn compile exec:java */
public class Main {

    public static void main(String[] args) {
        section1TraceSmallExample();
        section2AccuracyAcrossCardinalities();
        section3EffectOfPrecision();
        section4DuplicatesAreFree();
        section5MergeAcrossShards();
        section6UnionVersusSum();
    }

    // ------------------------------------------------------------------ 1
    private static void section1TraceSmallExample() {
        header("1. Trace on a tiny stream (p = 4, so m = 16 registers)");
        HyperLogLog hll = new HyperLogLog(4);
        String[] stream = {"alice", "bob", "carol", "alice", "dave", "bob", "erin"};
        System.out.printf("  %-8s %-18s %-9s %-5s %s%n", "element", "hash (hex)", "register", "rho", "effect");
        for (String s : stream) {
            long h = Hash64.of(s);
            int j = HyperLogLog.indexOf(h, 4);
            int rho = HyperLogLog.rho(h, 4);
            int before = hll.register(j);
            hll.addHash(h);
            String effect = hll.register(j) != before
                    ? "M[" + j + "]: " + before + " -> " + hll.register(j)
                    : "no change" + (before == rho ? " (duplicate or equal rho)" : "");
            System.out.printf("  %-8s 0x%016x %-9d %-5d %s%n", s, h, j, rho, effect);
        }
        System.out.printf("%n  True distinct: 5. Estimate: %.2f (tiny sets use Linear Counting)%n", hll.estimate());
        System.out.println("  Seeing 'alice' and 'bob' a second time did not change any register.");
    }

    // ------------------------------------------------------------------ 2
    private static void section2AccuracyAcrossCardinalities() {
        int p = 14;
        header("2. Accuracy across cardinalities (p = " + p + ")");
        System.out.printf("  Standard error 1.04/sqrt(m) = %.2f%%. Registers use %,d bytes here (1 byte each;%n  a packed 6-bit layout would need 12 KB).%n%n",
                HyperLogLog.standardError(p) * 100, new HyperLogLog(p).memoryBytes());
        System.out.printf("  %-14s %-14s %-9s%n", "true distinct", "estimate", "error");
        long[] sizes = {100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000};
        HyperLogLog hll = new HyperLogLog(p);
        long added = 0;
        for (long target : sizes) {
            for (; added < target; added++) {
                hll.add(added);
            }
            long est = hll.count();
            System.out.printf("  %-,14d %-,14d %+.2f%%%n", target, est, pctError(est, target));
        }
        System.out.println();
        System.out.println("  Same sketch size from 100 to 10 million elements. Error stays near 1%.");
    }

    // ------------------------------------------------------------------ 3
    private static void section3EffectOfPrecision() {
        header("3. Effect of precision (true distinct = 1,000,000; 20 runs per row)");
        System.out.printf("  %-4s %-9s %-12s %-14s %-14s %-12s%n",
                "p", "m", "memory", "theory error", "measured RMS", "worst run");
        int runs = 20;
        long n = 1_000_000;
        for (int p : new int[] {8, 10, 12, 14, 16}) {
            double sumSq = 0;
            double worst = 0;
            for (int r = 0; r < runs; r++) {
                HyperLogLog hll = new HyperLogLog(p);
                long base = (long) r * 10_000_000_000L;
                for (long i = 0; i < n; i++) {
                    hll.add(base + i);
                }
                double e = pctError(hll.estimate(), n);
                sumSq += e * e;
                worst = Math.max(worst, Math.abs(e));
            }
            double rms = Math.sqrt(sumSq / runs);
            System.out.printf("  %-4d %-,9d %-12s %-14s %-14s %-12s%n", p, 1 << p, bytes(1 << p),
                    String.format("%.2f%%", HyperLogLog.standardError(p) * 100),
                    String.format("%.2f%%", rms), String.format("%.2f%%", worst));
        }
        System.out.println();
        System.out.println("  Each +2 in p is 4x the memory and half the error (error ~ 1/sqrt(m)).");
    }

    // ------------------------------------------------------------------ 4
    private static void section4DuplicatesAreFree() {
        header("4. Duplicates are free (p = 14)");
        HyperLogLog hll = new HyperLogLog(14);
        for (int i = 0; i < 50_000; i++) {
            hll.add("user-" + i);
        }
        long once = hll.count();
        for (int round = 0; round < 20; round++) {
            for (int i = 0; i < 50_000; i++) {
                hll.add("user-" + i);
            }
        }
        long many = hll.count();
        System.out.printf("  50,000 distinct users added once:        estimate %,d%n", once);
        System.out.printf("  Same users added 20 more times (1.05M events): estimate %,d%n", many);
        System.out.println("  The estimate is identical: re-adding an element never changes a register.");
    }

    // ------------------------------------------------------------------ 5
    private static void section5MergeAcrossShards() {
        header("5. Merge across shards (8 servers, p = 14, users overlap between servers)");
        int shards = 8;
        int totalUsers = 400_000;
        java.util.Random random = new java.util.Random(42);
        HyperLogLog[] sketches = new HyperLogLog[shards];
        Set<Integer> exact = new HashSet<>();
        for (int s = 0; s < shards; s++) {
            sketches[s] = new HyperLogLog(14);
        }
        // Each server sees 200,000 events from a population of 400,000 users, so users repeat across servers.
        for (int s = 0; s < shards; s++) {
            for (int i = 0; i < 200_000; i++) {
                int user = random.nextInt(totalUsers);
                sketches[s].add(user);
                exact.add(user);
            }
        }
        long sumOfShards = 0;
        for (int s = 0; s < shards; s++) {
            System.out.printf("  server %d sees about %,d distinct users%n", s, sketches[s].count());
            sumOfShards += sketches[s].count();
        }
        HyperLogLog merged = HyperLogLog.union(sketches[0], java.util.Arrays.copyOfRange(sketches, 1, shards));
        System.out.printf("%n  True distinct users overall:         %,d%n", exact.size());
        System.out.printf("  Sum of per-server counts (WRONG):    %,d  (%+.1f%%)%n",
                sumOfShards, pctError(sumOfShards, exact.size()));
        System.out.printf("  Merged sketch (max of registers):    %,d  (%+.2f%%)%n",
                merged.count(), pctError(merged.count(), exact.size()));
        System.out.println("  Each server ships only its sketch, a few KB, not its user list.");
    }

    // ------------------------------------------------------------------ 6
    private static void section6UnionVersusSum() {
        header("6. Daily sketches combined into a weekly count (p = 14)");
        java.util.Random random = new java.util.Random(7);
        int population = 300_000;
        HyperLogLog[] days = new HyperLogLog[7];
        Set<Integer> week = new HashSet<>();
        System.out.printf("  %-6s %-14s %-14s%n", "day", "true unique", "estimate");
        for (int d = 0; d < 7; d++) {
            days[d] = new HyperLogLog(14);
            Set<Integer> today = new HashSet<>();
            for (int i = 0; i < 100_000; i++) {
                int user = random.nextInt(population);
                days[d].add(user);
                today.add(user);
                week.add(user);
            }
            System.out.printf("  %-6d %-,14d %-,14d%n", d + 1, today.size(), days[d].count());
        }
        HyperLogLog weekly = HyperLogLog.union(days[0], java.util.Arrays.copyOfRange(days, 1, 7));
        System.out.printf("%n  Unique users in the week, true:       %,d%n", week.size());
        System.out.printf("  Union of the 7 daily sketches:        %,d  (%+.2f%%)%n",
                weekly.count(), pctError(weekly.count(), week.size()));
        System.out.printf("  Memory kept: 7 sketches x %s = %s, instead of %,d user ids.%n",
                bytes(1 << 14), bytes(7 * (1 << 14)), week.size());
        System.out.println("  Store one sketch per day, then answer any date range by merging.");
    }

    // ------------------------------------------------------------------ helpers
    private static double pctError(double estimate, double truth) {
        return (estimate - truth) / truth * 100;
    }

    private static String bytes(long b) {
        return b >= 1024 ? String.format("%d KB", b / 1024) : b + " B";
    }

    private static void header(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
