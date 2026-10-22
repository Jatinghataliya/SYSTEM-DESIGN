package com.example.rsynccommons;

/**
 * Signature of a basis file block using hex string representation from Apache Commons Codec.
 */
public record CommonsBlockSignature(
        int blockIndex,
        long offset,
        int length,
        long rollingChecksum,
        String md5Hex
) {
}
