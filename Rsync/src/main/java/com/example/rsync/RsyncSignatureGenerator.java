package com.example.rsync;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates signature blocks for a basis file.
 */
public final class RsyncSignatureGenerator {

    public static final int DEFAULT_BLOCK_SIZE = 1024; // 1 KB default block size

    private final int blockSize;

    public RsyncSignatureGenerator() {
        this(DEFAULT_BLOCK_SIZE);
    }

    public RsyncSignatureGenerator(int blockSize) {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("Block size must be > 0, got: " + blockSize);
        }
        this.blockSize = blockSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Generate block signatures for the given basis data.
     *
     * @param basis basis file byte array
     * @return populated SignatureTable
     */
    public SignatureTable generate(byte[] basis) {
        if (basis == null || basis.length == 0) {
            return new SignatureTable(blockSize, List.of());
        }

        List<BlockSignature> signatures = new ArrayList<>();
        int totalBytes = basis.length;
        int blockIndex = 0;

        for (int offset = 0; offset < totalBytes; offset += blockSize) {
            int length = Math.min(blockSize, totalBytes - offset);
            long rolling = RollingChecksum.compute(basis, offset, length);
            byte[] strong = StrongChecksum.compute(basis, offset, length);

            signatures.add(new BlockSignature(blockIndex++, offset, length, rolling, strong));
        }

        return new SignatureTable(blockSize, signatures);
    }
}
