package com.example.consistenthashing;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

/**
 * One shared 64-bit hash for every strategy, so the comparison between strategies is fair.
 *
 * Murmur3 (via Guava) is fast and spreads well. Do NOT use String.hashCode() for this: it is a
 * 32-bit value with poor spread on similar strings such as "node-1#1", "node-1#2", and it is
 * not guaranteed to be stable across languages or versions, which matters when several
 * services must agree on placement.
 */
public final class Hashes {

    private static final HashFunction MURMUR = Hashing.murmur3_128();

    private Hashes() {
    }

    public static long hash(CharSequence text) {
        return MURMUR.hashString(text, StandardCharsets.UTF_8).asLong();
    }
}
