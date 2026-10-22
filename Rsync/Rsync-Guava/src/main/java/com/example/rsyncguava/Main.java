package com.example.rsyncguava;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("       RSYNC DELTA SYNC WITH GOOGLE GUAVA (SHA-256 + ADLER-32 ROLLING)          ");
        System.out.println("================================================================================");

        int blockSize = 32;
        GuavaRsyncEngine engine = new GuavaRsyncEngine(blockSize, Hashing.sha256());

        String basisText = "Google Guava provides rich collections, caching, primitives support, concurrency libraries, common annotations, string processing, I/O, and cryptographic hashing utilities.";
        String updatedText = "Google Guava provides rich IMMUTABLE collections, caching, primitives support, concurrency libraries, common annotations, string processing, I/O, and cryptographic hashing utilities.";

        byte[] basis = basisText.getBytes(StandardCharsets.UTF_8);
        byte[] updated = updatedText.getBytes(StandardCharsets.UTF_8);

        System.out.println("Basis size   : " + basis.length + " bytes");
        System.out.println("Updated size : " + updated.length + " bytes");

        GuavaSignatureTable sigTable = engine.generateSignatures(basis);
        System.out.println("Generated " + sigTable.size() + " basis block signatures (SHA-256)");

        List<GuavaDeltaInstruction> delta = engine.computeDelta(updated, sigTable);
        System.out.println("\nGenerated " + delta.size() + " delta instructions:");
        for (int i = 0; i < delta.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + delta.get(i));
        }

        byte[] reconstructed = engine.reconstruct(basis, delta, sigTable);
        boolean success = Arrays.equals(updated, reconstructed);

        System.out.println("\nReconstruction Match: " + (success ? "SUCCESS (Bit-for-bit identical)" : "FAILED"));
        System.out.println("Reconstructed text:\n" + new String(reconstructed, StandardCharsets.UTF_8));
    }
}
