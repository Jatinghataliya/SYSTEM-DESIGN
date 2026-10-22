package com.example.rsync;

/**
 * Delta instruction instructing the receiver to copy a block from the basis file.
 *
 * @param blockIndex 0-based index of the block in the basis file
 * @param length     number of bytes to copy (useful for final partial blocks)
 */
public record BlockMatchOp(int blockIndex, int length) implements DeltaOp {

    @Override
    public int byteSize() {
        // Opcode (1 byte) + Block Index (4 bytes) + Length (4 bytes) = 9 bytes
        return 9;
    }

    @Override
    public String toString() {
        return "BlockMatchOp[block=" + blockIndex + ", len=" + length + "]";
    }
}
