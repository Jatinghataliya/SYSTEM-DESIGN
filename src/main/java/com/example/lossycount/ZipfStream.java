package com.example.lossycount;

import java.util.Random;

/**
 * Generates a skewed stream of item ids {@code 1..universe}, where item {@code k} has probability
 * proportional to {@code 1 / k^exponent}. Real streams (queries, URLs, flows) look like this: a few
 * heavy items and a very long tail. Seeded, so runs are reproducible.
 */
public class ZipfStream {

    private final double[] cumulative;
    private final Random random;

    public ZipfStream(int universe, double exponent, long seed) {
        cumulative = new double[universe];
        double sum = 0;
        for (int k = 1; k <= universe; k++) {
            sum += 1.0 / Math.pow(k, exponent);
            cumulative[k - 1] = sum;
        }
        for (int i = 0; i < universe; i++) {
            cumulative[i] /= sum;
        }
        random = new Random(seed);
    }

    /** Next item id, in {@code 1..universe}. */
    public int next() {
        double u = random.nextDouble();
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] < u) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo + 1;
    }
}
