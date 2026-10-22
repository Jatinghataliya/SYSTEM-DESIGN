package com.example.rsync;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Computes strong cryptographic hash (MD5 by default) for block collision verification.
 */
public final class StrongChecksum {

    private static final String DEFAULT_ALGORITHM = "MD5";

    private StrongChecksum() {
    }

    /**
     * Compute MD5 hash bytes for a given slice of buffer.
     *
     * @param buffer data array
     * @param offset start offset
     * @param length slice length
     * @return 16-byte MD5 digest
     */
    public static byte[] compute(byte[] buffer, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DEFAULT_ALGORITHM);
            digest.update(buffer, offset, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Digest algorithm not available: " + DEFAULT_ALGORITHM, e);
        }
    }

    /**
     * Compute strong hash as hex string for easy debugging and logging.
     */
    public static String computeHex(byte[] buffer, int offset, int length) {
        byte[] digest = compute(buffer, offset, length);
        return HexFormat.of().formatHex(digest);
    }

    /**
     * Check if two digest arrays are equal in constant time.
     */
    public static boolean matches(byte[] expected, byte[] actual) {
        return Arrays.equals(expected, actual);
    }
}
