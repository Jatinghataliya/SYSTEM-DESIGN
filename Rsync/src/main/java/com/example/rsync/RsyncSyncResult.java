package com.example.rsync;

import java.util.List;

/**
 * Result encapsulating reconstructed data, delta operations, and transfer metrics.
 */
public record RsyncSyncResult(
        byte[] reconstructedData,
        List<DeltaOp> deltaOps,
        SignatureTable signatureTable,
        RsyncStats stats
) {
}
