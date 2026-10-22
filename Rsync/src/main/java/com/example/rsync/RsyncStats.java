package com.example.rsync;

import java.util.List;

/**
 * Statistics and network transfer metrics for an Rsync synchronization session.
 */
public record RsyncStats(
        long originalSourceSize,
        long basisSize,
        int totalBlocksInBasis,
        int matchedBlocksCount,
        long matchedBytes,
        int literalChunksCount,
        long literalBytesTransferred,
        long signatureOverheadBytes,
        long totalNetworkTransferredBytes,
        double bandwidthSavingsPercentage
) {
    public static RsyncStats compute(
            byte[] basis,
            byte[] source,
            SignatureTable table,
            List<DeltaOp> deltas
    ) {
        long sourceSize = source != null ? source.length : 0;
        long baseSize = basis != null ? basis.length : 0;
        int basisBlocks = table != null ? table.size() : 0;
        long sigOverhead = table != null ? table.estimatedWireSizeBytes() : 0;

        int matchedBlocks = 0;
        long matchedBytes = 0;
        int literalChunks = 0;
        long literalBytes = 0;
        long deltaOpsWireSize = 0;

        for (DeltaOp op : deltas) {
            deltaOpsWireSize += op.byteSize();
            if (op instanceof BlockMatchOp match) {
                matchedBlocks++;
                matchedBytes += match.length();
            } else if (op instanceof LiteralOp literal) {
                literalChunks++;
                literalBytes += literal.length();
            }
        }

        // Total wire transfer = Signatures sent from receiver + Delta stream sent from sender
        long totalNetwork = sigOverhead + deltaOpsWireSize;

        double savings = 0.0;
        if (sourceSize > 0) {
            savings = Math.max(0.0, (1.0 - ((double) totalNetwork / (double) sourceSize)) * 100.0);
        }

        return new RsyncStats(
                sourceSize,
                baseSize,
                basisBlocks,
                matchedBlocks,
                matchedBytes,
                literalChunks,
                literalBytes,
                sigOverhead,
                totalNetwork,
                savings
        );
    }
}
