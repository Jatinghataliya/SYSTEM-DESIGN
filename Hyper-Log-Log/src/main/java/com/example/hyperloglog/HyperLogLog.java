package com.example.hyperloglog;

import java.util.Arrays;

/**
 * HyperLogLog cardinality estimator (Flajolet, Fusy, Gandouet, Meunier 2007).
 *
 * <p>Estimates the number of <b>distinct</b> elements added, using {@code m = 2^p} one-byte
 * registers. The standard error is {@code 1.04 / sqrt(m)}.
 *
 * <p>Uses 64-bit hashes, so the large-range correction of the original paper is not needed. Small
 * counts use Linear Counting. This is plain HyperLogLog, not HyperLogLog++: there is no
 * bias-correction table and no sparse representation.
 *
 * <p>Not thread-safe. Give each thread its own sketch and {@link #merge} them, or guard with a lock.
 */
public final class HyperLogLog {

    public static final int MIN_PRECISION = 4;
    public static final int MAX_PRECISION = 18;

    private final int p;
    private final int m;
    private final byte[] registers;

    /** @param precision number of hash bits used to pick a register, in [4, 18] */
    public HyperLogLog(int precision) {
        if (precision < MIN_PRECISION || precision > MAX_PRECISION) {
            throw new IllegalArgumentException(
                    "precision must be in [" + MIN_PRECISION + ", " + MAX_PRECISION + "], got " + precision);
        }
        this.p = precision;
        this.m = 1 << precision;
        this.registers = new byte[m];
    }

    // ------------------------------------------------------------------ update

    public void add(String element) {
        addHash(Hash64.of(element));
    }

    public void add(long element) {
        addHash(Hash64.of(element));
    }

    /** Add an element given its already-computed, uniformly distributed 64-bit hash. */
    public void addHash(long hash) {
        int j = indexOf(hash, p);
        byte rho = rho(hash, p);
        if (rho > registers[j]) {
            registers[j] = rho;
        }
    }

    /** Register index: the first {@code p} bits of the hash. */
    static int indexOf(long hash, int p) {
        return (int) (hash >>> (64 - p));
    }

    /**
     * Position of the first 1-bit in the remaining {@code 64 - p} bits (leading zeros + 1).
     * A sentinel bit caps the value at {@code 64 - p + 1} when the remaining bits are all zero.
     */
    static byte rho(long hash, int p) {
        long rest = (hash << p) | (1L << (p - 1));
        return (byte) (Long.numberOfLeadingZeros(rest) + 1);
    }

    // ------------------------------------------------------------------ estimate

    /** Estimated number of distinct elements. */
    public double estimate() {
        double sum = 0;
        int zeros = 0;
        for (byte r : registers) {
            sum += Math.pow(2.0, -r);
            if (r == 0) {
                zeros++;
            }
        }
        double raw = alpha(m) * m * (double) m / sum;

        // Small range: raw estimate is biased low. Linear Counting is accurate while empties remain.
        if (raw <= 2.5 * m && zeros > 0) {
            return m * Math.log((double) m / zeros);
        }
        return raw;
    }

    /** {@link #estimate()} rounded to the nearest whole number. */
    public long count() {
        return Math.round(estimate());
    }

    private static double alpha(int m) {
        switch (m) {
            case 16:
                return 0.673;
            case 32:
                return 0.697;
            case 64:
                return 0.709;
            default:
                return 0.7213 / (1 + 1.079 / m);
        }
    }

    // ------------------------------------------------------------------ merge

    /**
     * Merge {@code other} into this sketch (register-wise maximum). The result equals the sketch that
     * would be built by adding both streams to one HyperLogLog. Both must use the same precision.
     */
    public void merge(HyperLogLog other) {
        if (other.p != p) {
            throw new IllegalArgumentException(
                    "cannot merge sketches with different precision: " + p + " vs " + other.p);
        }
        for (int i = 0; i < m; i++) {
            if (other.registers[i] > registers[i]) {
                registers[i] = other.registers[i];
            }
        }
    }

    /** New sketch holding the union of the given sketches. Inputs are not modified. */
    public static HyperLogLog union(HyperLogLog first, HyperLogLog... others) {
        HyperLogLog result = first.copy();
        for (HyperLogLog other : others) {
            result.merge(other);
        }
        return result;
    }

    public HyperLogLog copy() {
        HyperLogLog c = new HyperLogLog(p);
        System.arraycopy(registers, 0, c.registers, 0, m);
        return c;
    }

    // ------------------------------------------------------------------ inspection

    public int precision() {
        return p;
    }

    public int registerCount() {
        return m;
    }

    public int register(int index) {
        return registers[index];
    }

    public int zeroRegisters() {
        int zeros = 0;
        for (byte r : registers) {
            if (r == 0) {
                zeros++;
            }
        }
        return zeros;
    }

    /** Memory used by the registers in this implementation (one byte each). */
    public int memoryBytes() {
        return m;
    }

    /** Theoretical relative standard error of this sketch: {@code 1.04 / sqrt(m)}. */
    public double standardError() {
        return standardError(p);
    }

    public static double standardError(int precision) {
        return 1.04 / Math.sqrt(1 << precision);
    }

    public boolean sameRegistersAs(HyperLogLog other) {
        return p == other.p && Arrays.equals(registers, other.registers);
    }

    @Override
    public String toString() {
        return "HyperLogLog{p=" + p + ", m=" + m + ", estimate=" + count() + "}";
    }
}
