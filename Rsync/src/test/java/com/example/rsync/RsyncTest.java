package com.example.rsync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsyncTest {

    @Test
    @DisplayName("Identical files should produce only BlockMatchOps and zero literal bytes")
    void testIdenticalFiles() {
        Rsync rsync = new Rsync(64);
        byte[] data = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.".getBytes(StandardCharsets.UTF_8);

        RsyncSyncResult result = rsync.sync(data, data);

        assertArrayEquals(data, result.reconstructedData());
        assertEquals(0, result.stats().literalBytesTransferred());
        assertTrue(result.stats().matchedBlocksCount() > 0);
        for (DeltaOp op : result.deltaOps()) {
            assertTrue(op instanceof BlockMatchOp);
        }
    }

    @Test
    @DisplayName("Insertion at start shifts offsets but rolling checksum discovers subsequent matched blocks")
    void testInsertionAtStart() {
        Rsync rsync = new Rsync(32);
        String base = "AAAA_BLOCK_1___BBBB_BLOCK_2___CCCC_BLOCK_3___DDDD_BLOCK_4___";
        String src = "PREFIX_INSERTED_" + base;

        byte[] basisBytes = base.getBytes(StandardCharsets.UTF_8);
        byte[] srcBytes = src.getBytes(StandardCharsets.UTF_8);

        RsyncSyncResult result = rsync.sync(basisBytes, srcBytes);

        assertArrayEquals(srcBytes, result.reconstructedData());
        assertTrue(result.stats().matchedBlocksCount() > 0, "Should match shifted basis blocks");
    }

    @Test
    @DisplayName("Small modifications in middle should preserve surrounding blocks")
    void testMiddleModification() {
        Rsync rsync = new Rsync(32);
        String part1 = "Part1_Content_Block_Alpha_12345678901234567890_";
        String part2Original = "Part2_Original_Secret_Payload_9999999999999999_";
        String part2Modified = "Part2_MODIFIED_Secret_Payload_9999999999999999_";
        String part3 = "Part3_Content_Block_Omega_98765432109876543210_";

        byte[] basisBytes = (part1 + part2Original + part3).getBytes(StandardCharsets.UTF_8);
        byte[] srcBytes = (part1 + part2Modified + part3).getBytes(StandardCharsets.UTF_8);

        RsyncSyncResult result = rsync.sync(basisBytes, srcBytes);

        assertArrayEquals(srcBytes, result.reconstructedData());
        assertTrue(result.stats().matchedBlocksCount() >= 2, "Part1 and Part3 blocks should match");
    }

    @Test
    @DisplayName("Empty basis file creates literal ops for all source bytes")
    void testEmptyBasis() {
        Rsync rsync = new Rsync(64);
        byte[] basis = new byte[0];
        byte[] src = "Hello World! Rsync algorithm handles empty basis correctly.".getBytes(StandardCharsets.UTF_8);

        RsyncSyncResult result = rsync.sync(basis, src);

        assertArrayEquals(src, result.reconstructedData());
        assertEquals(src.length, result.stats().literalBytesTransferred());
        assertEquals(0, result.stats().matchedBlocksCount());
    }

    @Test
    @DisplayName("Empty source file produces empty reconstructed file")
    void testEmptySource() {
        Rsync rsync = new Rsync(64);
        byte[] basis = "Some existing basis file content".getBytes(StandardCharsets.UTF_8);
        byte[] src = new byte[0];

        RsyncSyncResult result = rsync.sync(basis, src);

        assertArrayEquals(src, result.reconstructedData());
        assertEquals(0, result.stats().literalBytesTransferred());
        assertEquals(0, result.stats().matchedBlocksCount());
    }

    @Test
    @DisplayName("Files smaller than block size sync correctly")
    void testFilesSmallerThanBlockSize() {
        Rsync rsync = new Rsync(128);
        byte[] basis = "Short content".getBytes(StandardCharsets.UTF_8);
        byte[] src = "Short updated content".getBytes(StandardCharsets.UTF_8);

        RsyncSyncResult result = rsync.sync(basis, src);
        assertArrayEquals(src, result.reconstructedData());
    }

    @Test
    @DisplayName("Completely disjoint files transfer as literal data")
    void testCompletelyDisjointFiles() {
        Rsync rsync = new Rsync(32);
        byte[] basis = "111111111111111111111111111111111111111111111111".getBytes(StandardCharsets.UTF_8);
        byte[] src = "222222222222222222222222222222222222222222222222".getBytes(StandardCharsets.UTF_8);

        RsyncSyncResult result = rsync.sync(basis, src);

        assertArrayEquals(src, result.reconstructedData());
        assertEquals(0, result.stats().matchedBlocksCount());
        assertEquals(src.length, result.stats().literalBytesTransferred());
    }

    @Test
    @DisplayName("Random binary mutation test with multiple block sizes and round-trip verification")
    void testRandomBinaryMutations() {
        Random random = new Random(12345);
        int originalSize = 10000;
        byte[] basis = new byte[originalSize];
        random.nextBytes(basis);

        // Create modified version with insertions, deletions, substitutions
        byte[] src = Arrays.copyOf(basis, originalSize + 500);
        // Inject 50 random byte changes
        for (int i = 0; i < 50; i++) {
            int pos = random.nextInt(src.length);
            src[pos] = (byte) random.nextInt(256);
        }

        int[] blockSizes = {64, 128, 256, 512};
        for (int bs : blockSizes) {
            Rsync rsync = new Rsync(bs);
            RsyncSyncResult result = rsync.sync(basis, src);

            assertArrayEquals(src, result.reconstructedData(), "Binary reconstruction mismatch at blockSize " + bs);
            assertTrue(result.stats().matchedBlocksCount() > 0, "Should match unmodified blocks at blockSize " + bs);
        }
    }
}
