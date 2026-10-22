package com.example.rsynccommons;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CommonsRsyncEngine {

    public sealed interface DeltaCommand permits DeltaCommand.Literal, DeltaCommand.Match {
        record Literal(byte[] data) implements DeltaCommand {
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
        }

        record Match(int blockIndex, int length) implements DeltaCommand {}
    }

    private final int blockSize;

    public CommonsRsyncEngine(int blockSize) {
        this.blockSize = blockSize;
    }

    public int getBlockSize() {
        return blockSize;
    }

    public CommonsSignatureTable generateSignatures(byte[] basis) {
        if (basis == null || basis.length == 0) {
            return new CommonsSignatureTable(blockSize, List.of());
        }

        List<CommonsBlockSignature> sigs = new ArrayList<>();
        int total = basis.length;
        int idx = 0;

        for (int offset = 0; offset < total; offset += blockSize) {
            int len = Math.min(blockSize, total - offset);
            long rolling = Fletcher32RollingChecksum.compute(basis, offset, len);
            byte[] slice = Arrays.copyOfRange(basis, offset, offset + len);
            String md5 = DigestUtils.md5Hex(slice);

            sigs.add(new CommonsBlockSignature(idx++, offset, len, rolling, md5));
        }

        return new CommonsSignatureTable(blockSize, sigs);
    }

    public List<DeltaCommand> computeDelta(byte[] source, CommonsSignatureTable table) {
        if (source == null || source.length == 0) {
            return Collections.emptyList();
        }

        if (table == null || table.isEmpty()) {
            return List.of(new DeltaCommand.Literal(source));
        }

        int n = source.length;
        int bs = table.getBlockSize();
        List<DeltaCommand> commands = new ArrayList<>();
        ByteArrayOutputStream pendingLiterals = new ByteArrayOutputStream();

        int i = 0;
        Fletcher32RollingChecksum rolling = new Fletcher32RollingChecksum();
        boolean windowValid = false;

        while (i < n) {
            int remaining = n - i;

            if (remaining >= bs) {
                if (!windowValid) {
                    rolling.init(source, i, bs);
                    windowValid = true;
                }

                long currentRoll = rolling.getValue();
                List<CommonsBlockSignature> candidates = table.findCandidates(currentRoll);
                CommonsBlockSignature matched = null;

                if (!candidates.isEmpty()) {
                    String md5 = null;
                    for (CommonsBlockSignature cand : candidates) {
                        if (cand.length() == bs) {
                            if (md5 == null) {
                                byte[] slice = Arrays.copyOfRange(source, i, i + bs);
                                md5 = DigestUtils.md5Hex(slice);
                            }
                            if (cand.md5Hex().equalsIgnoreCase(md5)) {
                                matched = cand;
                                break;
                            }
                        }
                    }
                }

                if (matched != null) {
                    if (pendingLiterals.size() > 0) {
                        commands.add(new DeltaCommand.Literal(pendingLiterals.toByteArray()));
                        pendingLiterals.reset();
                    }
                    commands.add(new DeltaCommand.Match(matched.blockIndex(), matched.length()));
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
                // Partial block
                CommonsBlockSignature matched = null;
                long partialRoll = Fletcher32RollingChecksum.compute(source, i, remaining);
                List<CommonsBlockSignature> candidates = table.findCandidates(partialRoll);

                if (!candidates.isEmpty()) {
                    String md5 = null;
                    for (CommonsBlockSignature cand : candidates) {
                        if (cand.length() == remaining) {
                            if (md5 == null) {
                                byte[] slice = Arrays.copyOfRange(source, i, i + remaining);
                                md5 = DigestUtils.md5Hex(slice);
                            }
                            if (cand.md5Hex().equalsIgnoreCase(md5)) {
                                matched = cand;
                                break;
                            }
                        }
                    }
                }

                if (matched != null) {
                    if (pendingLiterals.size() > 0) {
                        commands.add(new DeltaCommand.Literal(pendingLiterals.toByteArray()));
                        pendingLiterals.reset();
                    }
                    commands.add(new DeltaCommand.Match(matched.blockIndex(), matched.length()));
                    i += remaining;
                } else {
                    pendingLiterals.write(source[i]);
                    i++;
                }
            }
        }

        if (pendingLiterals.size() > 0) {
            commands.add(new DeltaCommand.Literal(pendingLiterals.toByteArray()));
        }

        return Collections.unmodifiableList(commands);
    }

    public byte[] reconstruct(byte[] basis, List<DeltaCommand> commands, CommonsSignatureTable table) {
        if (commands == null || commands.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (DeltaCommand cmd : commands) {
                if (cmd instanceof DeltaCommand.Literal lit) {
                    out.write(lit.data());
                } else if (cmd instanceof DeltaCommand.Match match) {
                    CommonsBlockSignature sig = table.getByIndex(match.blockIndex());
                    out.write(basis, (int) sig.offset(), match.length());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Reconstruction failed", e);
        }

        return out.toByteArray();
    }
}
