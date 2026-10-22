package com.merkletree;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class providing SHA-256 hashing used by all Merkle Tree nodes.
 */
public class HashUtils {

    private HashUtils() { /* utility class — no instantiation */ }

    /**
     * Computes the SHA-256 hash of the given input string and returns it
     * as a lowercase hexadecimal string (64 characters).
     *
     * @param input the data to hash
     * @return 64-character hex string
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Returns a short readable prefix of a hash (first 8 characters + "...").
     */
    public static String shortHash(String hash) {
        return hash.substring(0, 8) + "...";
    }
}
