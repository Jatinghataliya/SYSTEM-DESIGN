package com.example.rsyncguava;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rsync Delta Synchronization Engine powered by Google Guava.
 */
public final class GuavaRsyncEngine {

    private final int blockSize;
    @SuppressWarnings("deprecation")
    private final HashFunction strongHashFunction;

    public GuavaRsyncEngine(int blockSize) {
        this(blockSize, Hashing.sha256());
    }

    public GuavaRsyncEngine(int blockSize, HashFunction strongHashFunction) {
        this.blockSize = blockSize;
        this.strongHashFunction = strongHashFunction;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public HashFunction getStrongHashFunction() {
        return strongHashFunction;
    }

    /**
     * Receiver side: Generate signature table for basis data.
     */
    public GuavaSignatureTable generateSignatures(byte[] basis) {
        if (basis == null || basis.length == 0) {
            return new GuavaSignatureTable(blockSize, List.of());
        }

        List<GuavaBlockSignature> sigs = new ArrayList<>();
        int total = basis.length;
        int idx = 0;

        for (int offset = 0; offset < total; offset += blockSize) {
            int len = Math.min(blockSize, total - offset);
            long rolling = Adler32RollingChecksum.compute(basis, offset, len);
            HashCode strong = strongHashFunction.hashBytes(basis, offset, len);

            sigs.add(new GuavaBlockSignature(idx++, offset, len, rolling, strong));
        }

        return new GuavaSignatureTable(blockSize, sigs);
    }

    /**
     * Sender side: Compute delta instructions using sliding window Adler32 + Guava strong hash.
     */
    public List<GuavaDeltaInstruction> computeDelta(byte[] source, GuavaSignatureTable table) {
        if (source == null || source.length == 0) {
            return Collections.emptyList();
        }

        if (table == null || table.isEmpty()) {
            return List.of(new GuavaDeltaInstruction.Literal(source));
        }

        int n = source.length;
        int bs = table.getBlockSize();
        List<GuavaDeltaInstruction> instructions = new ArrayList<>();
        ByteArrayOutputStream pendingLiterals = new ByteArrayOutputStream();

        int i = 0;
        Adler32RollingChecksum rolling = new Adler32RollingChecksum();
        boolean windowValid = false;

        while (i < n) {
            int remaining = n - i;

            if (remaining >= bs) {
                if (!windowValid) {
                    rolling.init(source, i, bs);
                    windowValid = true;
                }

                long currentRoll = rolling.getValue();
                List<GuavaBlockSignature> candidates = table.findCandidates(currentRoll);
                GuavaBlockSignature matched = null;

                if (!candidates.isEmpty()) {
                    HashCode strong = null;
                    for (GuavaBlockSignature cand : candidates) {
                        if (cand.length() == bs) {
                            if (strong == null) {
                                strong = strongHashFunction.hashBytes(source, i, bs);
                            }
                            if (cand.strongHash().equals(strong)) {
                                matched = cand;
                                break;
                            }
                        }
                    }
                }

                if (matched != null) {
                    if (pendingLiterals.size() > 0) {
                        instructions.add(new GuavaDeltaInstruction.Literal(pendingLiterals.toByteArray()));
                        pendingLiterals.reset();
                    }
                    instructions.add(new GuavaDeltaInstruction.BlockMatch(matched.blockIndex(), matched.length()));
                    i += bs;
                    windowValid = false;
                } else {
                    pendingLiterals.write(source[i]);
                    if (i + bs < n) {
                        rolling.roll(source[i], source[i + bs]);
                    } else {
                        windowValid = false;
                    }
                    i++;
                }
            } else {
                // Partial block at end
                GuavaBlockSignature matched = null;
                long partialRoll = Adler32RollingChecksum.compute(source, i, remaining);
                List<GuavaBlockSignature> candidates = table.findCandidates(partialRoll);

                if (!candidates.isEmpty()) {
                    HashCode strong = null;
                    for (GuavaBlockSignature cand : candidates) {
                        if (cand.length() == remaining) {
                            if (strong == null) {
                                strong = strongHashFunction.hashBytes(source, i, remaining);
                            }
                            if (cand.strongHash().equals(strong)) {
                                matched = cand;
                                break;
                            }
                        }
                    }
                }

                if (matched != null) {
                    if (pendingLiterals.size() > 0) {
                        instructions.add(new GuavaDeltaInstruction.Literal(pendingLiterals.toByteArray()));
                        pendingLiterals.reset();
                    }
                    instructions.add(new GuavaDeltaInstruction.BlockMatch(matched.blockIndex(), matched.length()));
                    i += remaining;
                } else {
                    pendingLiterals.write(source[i]);
                    i++;
                }
            }
        }

        if (pendingLiterals.size() > 0) {
            instructions.add(new GuavaDeltaInstruction.Literal(pendingLiterals.toByteArray()));
        }

        return Collections.unmodifiableList(instructions);
    }

    /**
     * Receiver side: Reconstruct source from basis and delta instructions.
     */
    public byte[] reconstruct(byte[] basis, List<GuavaDeltaInstruction> instructions, GuavaSignatureTable table) {
        if (instructions == null || instructions.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (GuavaDeltaInstruction inst : instructions) {
                if (inst instanceof GuavaDeltaInstruction.Literal lit) {
                    out.write(lit.data());
                } else if (inst instanceof GuavaDeltaInstruction.BlockMatch match) {
                    GuavaBlockSignature sig = table.getByIndex(match.blockIndex());
                    out.write(basis, (int) sig.offset(), match.length());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Reconstruction failed", e);
        }

        return out.toByteArray();
    }
}
