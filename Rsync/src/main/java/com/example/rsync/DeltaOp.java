package com.example.rsync;

/**
 * Base interface representing a single instruction in the Rsync delta stream.
 */
public sealed interface DeltaOp permits LiteralOp, BlockMatchOp {

    /**
     * Approximate wire size of this operation in bytes.
     */
    int byteSize();
}
