package com.example.rsync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollingChecksumTest {

    @Test
    @DisplayName("Rolling checksum roll() step-by-step should match compute() for all windows")
    void testRollingMatchesDirectCompute() {
        byte[] data = "The quick brown fox jumps over the lazy dog. System design rsync algorithm in Java 17.".getBytes(StandardCharsets.UTF_8);
        int windowSize = 16;

        RollingChecksum rolling = new RollingChecksum();
        rolling.init(data, 0, windowSize);

        long direct0 = RollingChecksum.compute(data, 0, windowSize);
        assertEquals(direct0, rolling.getValue(), "Initial window checksum should match direct compute");

        for (int i = 0; i + windowSize < data.length; i++) {
            byte outByte = data[i];
            byte inByte = data[i + windowSize];

            long rolledVal = rolling.roll(outByte, inByte);
            long expectedVal = RollingChecksum.compute(data, i + 1, windowSize);

            assertEquals(expectedVal, rolledVal, "Mismatch at window index " + (i + 1));
            assertEquals(expectedVal, rolling.getValue());
        }
    }

    @Test
    @DisplayName("Rolling checksum works accurately across random binary data")
    void testRollingWithRandomBinaryData() {
        Random random = new Random(42);
        byte[] data = new byte[2048];
        random.nextBytes(data);

        int[] windowSizes = {8, 16, 32, 64, 128, 512};

        for (int windowSize : windowSizes) {
            RollingChecksum rolling = new RollingChecksum();
            rolling.init(data, 0, windowSize);

            for (int i = 0; i + windowSize < data.length; i++) {
                byte outByte = data[i];
                byte inByte = data[i + windowSize];

                long rolledVal = rolling.roll(outByte, inByte);
                long directVal = RollingChecksum.compute(data, i + 1, windowSize);

                assertEquals(directVal, rolledVal, "Random binary rolling mismatch at windowSize " + windowSize + " index " + i);
            }
        }
    }
}
