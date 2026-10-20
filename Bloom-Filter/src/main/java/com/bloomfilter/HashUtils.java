package com.bloomfilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

public class HashUtils {

    /**
     * Generate k bit positions for a given item using SHA-256 with seed salting.
     * Each seed produces an independent hash position in [0, bitArraySize).
     */
    public static int[] getHashPositions(String item, int numHashes, int bitArraySize) {
        int[] positions = new int[numHashes];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int seed = 0; seed < numHashes; seed++) {
                String input = seed + ":" + item;
                byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                BigInteger bigInt = new BigInteger(1, hashBytes);
                positions[seed] = bigInt.mod(BigInteger.valueOf(bitArraySize)).intValue();
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
        return positions;
    }
}
