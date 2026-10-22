package com.example.rsync;

import java.util.Arrays;

/**
 * Signature metadata for a fixed-size chunk/block of a basis file.
 *
 * @param blockIndex      sequential index of the block (0, 1, 2, ...)
 * @param offset          byte offset in the basis file
 * @param length          length of this block in bytes
 * @param rollingChecksum 32-bit fast rolling checksum
 * @param strongChecksum  128-bit strong hash (MD5)
 */
public record BlockSignature(
        int blockIndex,
        long offset,
        int length,
        long rollingChecksum,
        byte[] strongChecksum
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockSignature that)) return false;
        return blockIndex == that.blockIndex &&
                offset == that.offset &&
                length == that.length &&
                rollingChecksum == that.rollingChecksum &&
                Arrays.equals(strongChecksum, that.strongChecksum);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(rollingChecksum);
        result = 31 * result + Integer.hashCode(blockIndex);
        result = 31 * result + Long.hashCode(offset);
        result = 31 * result + Integer.hashCode(length);
        result = 31 * result + Arrays.hashCode(strongChecksum);
        return result;
    }

    @Override
    public String toString() {
        return "BlockSignature[index=" + blockIndex +
                ", offset=" + offset +
                ", len=" + length +
                ", roll=" + Long.toHexString(rollingChecksum) +
                "]";
    }
}
