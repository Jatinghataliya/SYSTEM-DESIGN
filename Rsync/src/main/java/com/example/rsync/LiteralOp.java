package com.example.rsync;

import java.util.Arrays;

/**
 * Delta instruction instructing the receiver to insert raw literal bytes.
 *
 * @param data raw byte payload
 */
public record LiteralOp(byte[] data) implements DeltaOp {

    public LiteralOp {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    public int length() {
        return data.length;
    }

    @Override
    public int byteSize() {
        // Opcode (1 byte) + Length (4 bytes) + payload
        return 5 + data.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LiteralOp that)) return false;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "LiteralOp[len=" + data.length + " bytes]";
    }
}
