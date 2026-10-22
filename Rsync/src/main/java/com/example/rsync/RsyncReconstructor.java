package com.example.rsync;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Reconstructs target file on receiver side by applying delta stream onto the basis file.
 */
public final class RsyncReconstructor {

    /**
     * Reconstruct destination data from basis data and delta operations.
     *
     * @param basis    basis byte array (may be null or empty)
     * @param deltaOps list of delta instructions
     * @param table    signature table containing block offsets/lengths in basis
     * @return reconstructed byte array
     */
    public byte[] reconstruct(byte[] basis, List<DeltaOp> deltaOps, SignatureTable table) {
        if (deltaOps == null || deltaOps.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            for (DeltaOp op : deltaOps) {
                if (op instanceof LiteralOp literal) {
                    out.write(literal.data());
                } else if (op instanceof BlockMatchOp match) {
                    if (basis == null || basis.length == 0 || table == null) {
                        throw new IllegalStateException("Cannot apply BlockMatchOp without basis data");
                    }
                    BlockSignature sig = table.getByIndex(match.blockIndex());
                    int offset = (int) sig.offset();
                    int length = match.length();

                    if (offset + length > basis.length) {
                        throw new IndexOutOfBoundsException("Block out of bounds in basis: offset=" + offset + ", len=" + length);
                    }

                    out.write(basis, offset, length);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error during reconstruction", e);
        }

        return out.toByteArray();
    }
}
