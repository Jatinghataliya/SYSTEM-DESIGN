package com.example.hyperloglog;

import java.nio.charset.StandardCharsets;

/**
 * 64-bit hash functions used by {@link HyperLogLog}.
 *
 * <p>HyperLogLog needs hash values whose bits look uniformly random. Both functions end with the
 * MurmurHash3 64-bit finalizer ({@code fmix64}), which gives full avalanche: flipping any input bit
 * flips each output bit with probability about 1/2.
 */
public final class Hash64 {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private Hash64() {
    }

    /** Hash a 64-bit integer. */
    public static long of(long value) {
        return fmix64(value + GOLDEN_GAMMA);
    }

    /** Hash a string using FNV-1a over its UTF-8 bytes, then the Murmur3 finalizer. */
    public static long of(String value) {
        long h = FNV_OFFSET;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xffL);
            h *= FNV_PRIME;
        }
        return fmix64(h);
    }

    /** MurmurHash3 64-bit finalizer. */
    static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }
}
