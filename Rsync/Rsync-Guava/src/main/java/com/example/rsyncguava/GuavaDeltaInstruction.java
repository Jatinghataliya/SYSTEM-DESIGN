package com.example.rsyncguava;

import java.util.Arrays;

/**
 * Delta instruction types for Guava Rsync.
 */
public sealed interface GuavaDeltaInstruction permits GuavaDeltaInstruction.Literal, GuavaDeltaInstruction.BlockMatch {

    record Literal(byte[] data) implements GuavaDeltaInstruction {
        public Literal {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Literal that)) return false;
            return Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }

        @Override
        public String toString() {
            return "Literal[" + data.length + " bytes]";
        }
    }

    record BlockMatch(int blockIndex, int length) implements GuavaDeltaInstruction {
        @Override
        public String toString() {
            return "BlockMatch[idx=" + blockIndex + ", len=" + length + "]";
        }
    }
}
