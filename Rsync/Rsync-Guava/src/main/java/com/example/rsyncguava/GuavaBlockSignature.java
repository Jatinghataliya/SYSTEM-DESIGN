package com.example.rsyncguava;

import com.google.common.hash.HashCode;

/**
 * Block signature using Google Guava's HashCode representation.
 */
public record GuavaBlockSignature(
        int blockIndex,
        long offset,
        int length,
        long rollingAdler32,
        HashCode strongHash
) {
    @Override
    public String toString() {
        return "GuavaBlockSig[#=" + blockIndex + ", off=" + offset + ", len=" + length + ", strong=" + strongHash + "]";
    }
}
