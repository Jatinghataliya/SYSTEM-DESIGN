package com.example.rsynccommons;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonsRsyncTest {

    @Test
    @DisplayName("Fletcher32 rolling checksum matches direct compute across all windows")
    void testFletcher32RollingChecksum() {
        byte[] data = "Testing Apache Commons Rsync Fletcher-32 rolling checksum byte stream.".getBytes(StandardCharsets.UTF_8);
        int window = 16;

        Fletcher32RollingChecksum rolling = new Fletcher32RollingChecksum();
        rolling.init(data, 0, window);

        long direct0 = Fletcher32RollingChecksum.compute(data, 0, window);
        assertEquals(direct0, rolling.getValue());

        for (int i = 0; i + window < data.length; i++) {
            byte outByte = data[i];
            byte inByte = data[i + window];

            long rolled = rolling.roll(outByte, inByte);
            long direct = Fletcher32RollingChecksum.compute(data, i + 1, window);

            assertEquals(direct, rolled);
        }
    }

    @Test
    @DisplayName("Apache Commons engine successfully computes delta and reconstructs file")
    void testCommonsRsyncSync() {
        CommonsRsyncEngine engine = new CommonsRsyncEngine(32);

        String basis = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer nec odio. Praesent libero. Sed cursus ante dapibus diam.";
        String updated = "PREFIX_INSERTED_Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer nec odio. Praesent libero. Sed cursus ante dapibus diam.";

        byte[] basisBytes = basis.getBytes(StandardCharsets.UTF_8);
        byte[] updatedBytes = updated.getBytes(StandardCharsets.UTF_8);

        CommonsSignatureTable table = engine.generateSignatures(basisBytes);
        List<CommonsRsyncEngine.DeltaCommand> deltas = engine.computeDelta(updatedBytes, table);
        byte[] reconstructed = engine.reconstruct(basisBytes, deltas, table);

        assertArrayEquals(updatedBytes, reconstructed);
    }

    @Test
    @DisplayName("Apache Commons engine handles randomized byte streams")
    void testCommonsRsyncRandomBytes() {
        CommonsRsyncEngine engine = new CommonsRsyncEngine(64);
        Random random = new Random(42);

        byte[] basis = new byte[3000];
        random.nextBytes(basis);

        byte[] updated = new byte[3200];
        System.arraycopy(basis, 0, updated, 100, 2800); // 100 byte offset shift

        CommonsSignatureTable table = engine.generateSignatures(basis);
        List<CommonsRsyncEngine.DeltaCommand> deltas = engine.computeDelta(updated, table);
        byte[] reconstructed = engine.reconstruct(basis, deltas, table);

        assertArrayEquals(updated, reconstructed);
    }
}
