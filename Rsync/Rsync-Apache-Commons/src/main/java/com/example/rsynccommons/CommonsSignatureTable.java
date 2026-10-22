package com.example.rsynccommons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommonsSignatureTable {

    private final int blockSize;
    private final List<CommonsBlockSignature> signatures;
    private final Map<Long, List<CommonsBlockSignature>> lookup;

    public CommonsSignatureTable(int blockSize, List<CommonsBlockSignature> signatures) {
        this.blockSize = blockSize;
        this.signatures = Collections.unmodifiableList(new ArrayList<>(signatures));
        this.lookup = new HashMap<>(signatures.size());

        for (CommonsBlockSignature sig : signatures) {
            lookup.computeIfAbsent(sig.rollingChecksum(), k -> new ArrayList<>(1)).add(sig);
        }
    }

    public int getBlockSize() {
        return blockSize;
    }

    public List<CommonsBlockSignature> getSignatures() {
        return signatures;
    }

    public int size() {
        return signatures.size();
    }

    public boolean isEmpty() {
        return signatures.isEmpty();
    }

    public List<CommonsBlockSignature> findCandidates(long rollingChecksum) {
        return lookup.getOrDefault(rollingChecksum, Collections.emptyList());
    }

    public CommonsBlockSignature getByIndex(int index) {
        return signatures.get(index);
    }
}
