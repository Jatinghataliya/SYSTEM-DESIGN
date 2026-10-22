package com.example.rsyncguava;

import com.google.common.hash.Hashing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuavaRsyncTest {

    @Test
    @DisplayName("Adler-32 rolling calculation matches step-by-step direct Adler32")
    void testAdler32RollingChecksum() {
        byte[] data = "Guava Rsync Adler-32 rolling checksum validation test vector string.".getBytes(StandardCharsets.UTF_8);
        int window = 12;

        Adler32RollingChecksum rolling = new Adler32RollingChecksum();
        rolling.init(data, 0, window);

        long direct0 = Adler32RollingChecksum.compute(data, 0, window);
        assertEquals(direct0, rolling.getValue());

        for (int i = 0; i + window < data.length; i++) {
            byte outByte = data[i];
            byte inByte = data[i + window];

            long rolled = rolling.roll(outByte, inByte);
            long direct = Adler32RollingChecksum.compute(data, i + 1, window);

            assertEquals(direct, rolled, "Adler-32 rolling mismatch at index " + (i + 1));
        }
    }

    @Test
    @DisplayName("Guava Rsync engine accurately delta-syncs modified text with SHA-256")
    void testGuavaRsyncSync() {
        GuavaRsyncEngine engine = new GuavaRsyncEngine(16, Hashing.sha256());

        String basis = "AlphaBetaGammaDeltaEpsilonZetaEtaThetaIotaKappaLambdaMuNuXiOmicronPiRhoSigmaTauUpsilonPhiChiPsiOmega";
        String updated = "AlphaBetaGammaDelta_MODIFIED_ZetaEtaThetaIotaKappaLambdaMuNuXiOmicronPiRhoSigmaTauUpsilonPhiChiPsiOmega";

        byte[] basisBytes = basis.getBytes(StandardCharsets.UTF_8);
        byte[] updatedBytes = updated.getBytes(StandardCharsets.UTF_8);

        GuavaSignatureTable sigs = engine.generateSignatures(basisBytes);
        List<GuavaDeltaInstruction> deltas = engine.computeDelta(updatedBytes, sigs);
        byte[] reconstructed = engine.reconstruct(basisBytes, deltas, sigs);

        assertArrayEquals(updatedBytes, reconstructed);
    }

    @Test
    @DisplayName("Guava Rsync handles random binary payloads")
    void testGuavaRsyncRandomBinary() {
        GuavaRsyncEngine engine = new GuavaRsyncEngine(64, Hashing.sha256());
        Random random = new Random(99);

        byte[] basis = new byte[4096];
        random.nextBytes(basis);

        byte[] updated = new byte[4200];
        System.arraycopy(basis, 0, updated, 50, 4000); // Shift by 50 bytes

        GuavaSignatureTable sigs = engine.generateSignatures(basis);
        List<GuavaDeltaInstruction> deltas = engine.computeDelta(updated, sigs);
        byte[] reconstructed = engine.reconstruct(basis, deltas, sigs);

        assertArrayEquals(updated, reconstructed);
    }
}
