package com.example.rsynccommons;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   RSYNC DELTA SYNC WITH APACHE COMMONS (CODEC MD5 + FLETCHER-32 ROLLING)       ");
        System.out.println("================================================================================");

        int blockSize = 32;
        CommonsRsyncEngine engine = new CommonsRsyncEngine(blockSize);

        String basisText = "Apache Commons Codec provides implementations of common encoders and decoders such as Base64, Hex, Phonetic and URLs as well as cryptographic digest utilities.";
        String updatedText = "Apache Commons Codec provides OPTIMIZED implementations of common encoders and decoders such as Base64, Hex, Phonetic and URLs as well as cryptographic digest utilities.";

        byte[] basis = basisText.getBytes(StandardCharsets.UTF_8);
        byte[] updated = updatedText.getBytes(StandardCharsets.UTF_8);

        CommonsSignatureTable table = engine.generateSignatures(basis);
        System.out.println("Basis size: " + basis.length + " bytes -> " + table.size() + " blocks");

        List<CommonsRsyncEngine.DeltaCommand> deltas = engine.computeDelta(updated, table);
        System.out.println("Computed " + deltas.size() + " delta commands:");
        for (int i = 0; i < deltas.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + deltas.get(i));
        }

        byte[] reconstructed = engine.reconstruct(basis, deltas, table);
        boolean success = Arrays.equals(updated, reconstructed);

        System.out.println("\nReconstruction Match: " + (success ? "SUCCESS" : "FAILED"));
    }
}
