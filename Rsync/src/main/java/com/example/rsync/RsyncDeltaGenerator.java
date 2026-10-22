package com.example.rsync;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes difference (deltas) between source data and the basis file signature table.
 */
public final class RsyncDeltaGenerator {

    /**
     * Compute delta operations from source byte array against basis signatures.
     *
     * @param source source byte array (the newer / updated version)
     * @param table  signature table from the basis / destination file
     * @return list of DeltaOp (LiteralOp and BlockMatchOp)
     */
    public List<DeltaOp> generateDelta(byte[] source, SignatureTable table) {
        if (source == null || source.length == 0) {
            return Collections.emptyList();
        }

        if (table == null || table.isEmpty()) {
            return List.of(new LiteralOp(source));
        }

        int blockSize = table.getBlockSize();
        int n = source.length;
        List<DeltaOp> deltaOps = new ArrayList<>();
        ByteArrayOutputStream pendingLiterals = new ByteArrayOutputStream();

        int i = 0;
        RollingChecksum rolling = new RollingChecksum();
        boolean windowValid = false;

        while (i < n) {
            int remaining = n - i;

            if (remaining >= blockSize) {
                if (!windowValid) {
                    rolling.init(source, i, blockSize);
                    windowValid = true;
                }

                long currentRoll = rolling.getValue();
                List<BlockSignature> candidates = table.findCandidates(currentRoll);
                BlockSignature matched = null;

                if (!candidates.isEmpty()) {
                    byte[] strong = null;
                    for (BlockSignature cand : candidates) {
                        if (cand.length() == blockSize) {
                            if (strong == null) {
                                strong = StrongChecksum.compute(source, i, blockSize);
                            }
                            if (StrongChecksum.matches(cand.strongChecksum(), strong)) {
                                matched = cand;
                                break;
                            }
                        }
                    }
                }

                if (matched != null) {
                    // Flush accumulated literals
                    if (pendingLiterals.size() > 0) {
                        deltaOps.add(new LiteralOp(pendingLiterals.toByteArray()));
                        pendingLiterals.reset();
                    }

                    deltaOps.add(new BlockMatchOp(matched.blockIndex(), matched.length()));
                    i += blockSize;
                    windowValid = false; // Need re-init after jumping blockSize
                } else {
                    // No match at offset i, buffer byte as literal and roll window
                    pendingLiterals.write(source[i]);
                    if (i + blockSize < n) {
                        rolling.roll(source[i], source[i + blockSize]);
                    } else {
                        windowValid = false;
                    }
                    i++;
                }
            } else {
                // Remaining bytes are less than full blockSize.
                // Check if they match a trailing partial block in the basis table.
                BlockSignature matched = null;
                long partialRoll = RollingChecksum.compute(source, i, remaining);
                List<BlockSignature> candidates = table.findCandidates(partialRoll);

                if (!candidates.isEmpty()) {
                    byte[] strong = null;
                    for (BlockSignature cand : candidates) {
                        if (cand.length() == remaining) {
                            if (strong == null) {
                                strong = StrongChecksum.compute(source, i, remaining);
                            }
                            if (StrongChecksum.matches(cand.strongChecksum(), strong)) {
                                matched = cand;
                                break;
                            }
                        }
                    }
                }

                if (matched != null) {
                    if (pendingLiterals.size() > 0) {
                        deltaOps.add(new LiteralOp(pendingLiterals.toByteArray()));
                        pendingLiterals.reset();
                    }
                    deltaOps.add(new BlockMatchOp(matched.blockIndex(), matched.length()));
                    i += remaining;
                } else {
                    pendingLiterals.write(source[i]);
                    i++;
                }
            }
        }

        // Flush any remaining literals
        if (pendingLiterals.size() > 0) {
            deltaOps.add(new LiteralOp(pendingLiterals.toByteArray()));
        }

        return Collections.unmodifiableList(deltaOps);
    }
}
