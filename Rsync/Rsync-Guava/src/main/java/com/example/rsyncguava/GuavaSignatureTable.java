package com.example.rsyncguava;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import java.util.List;

/**
 * Signature lookup table backed by Guava ImmutableListMultimap.
 */
public final class GuavaSignatureTable {

    private final int blockSize;
    private final ImmutableList<GuavaBlockSignature> signatures;
    private final ImmutableListMultimap<Long, GuavaBlockSignature> lookupByRolling;

    public GuavaSignatureTable(int blockSize, List<GuavaBlockSignature> sigs) {
        this.blockSize = blockSize;
        this.signatures = ImmutableList.copyOf(sigs);

        ImmutableListMultimap.Builder<Long, GuavaBlockSignature> builder = ImmutableListMultimap.builder();
        for (GuavaBlockSignature sig : sigs) {
            builder.put(sig.rollingAdler32(), sig);
        }
        this.lookupByRolling = builder.build();
    }

    public int getBlockSize() {
        return blockSize;
    }

    public ImmutableList<GuavaBlockSignature> getSignatures() {
        return signatures;
    }

    public int size() {
        return signatures.size();
    }

    public boolean isEmpty() {
        return signatures.isEmpty();
    }

    public List<GuavaBlockSignature> findCandidates(long rollingChecksum) {
        return lookupByRolling.get(rollingChecksum);
    }

    public GuavaBlockSignature getByIndex(int index) {
        return signatures.get(index);
    }
}
