package com.example.rsync;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fast lookup table for basis block signatures indexed by their 32-bit rolling checksum.
 */
public final class SignatureTable {

    private final int blockSize;
    private final List<BlockSignature> signatures;
    private final Map<Long, List<BlockSignature>> lookup;

    public SignatureTable(int blockSize, List<BlockSignature> signatures) {
        this.blockSize = blockSize;
        this.signatures = Collections.unmodifiableList(new ArrayList<>(signatures));
        this.lookup = new HashMap<>(signatures.size());

        for (BlockSignature sig : signatures) {
            this.lookup.computeIfAbsent(sig.rollingChecksum(), k -> new ArrayList<>(1)).add(sig);
        }
    }

    public int getBlockSize() {
        return blockSize;
    }

    public List<BlockSignature> getSignatures() {
        return signatures;
    }

    public int size() {
        return signatures.size();
    }

    public boolean isEmpty() {
        return signatures.isEmpty();
    }

    /**
     * Find candidate block signatures matching the given rolling checksum.
     *
     * @param rollingChecksum 32-bit rolling checksum
     * @return list of matching signatures, or empty list if no match
     */
    public List<BlockSignature> findCandidates(long rollingChecksum) {
        List<BlockSignature> list = lookup.get(rollingChecksum);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Lookup a block signature by block index.
     */
    public BlockSignature getByIndex(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= signatures.size()) {
            throw medicalBoundsException(blockIndex);
        }
        return signatures.get(blockIndex);
    }

    private IndexOutOfBoundsException medicalBoundsException(int blockIndex) {
        return new IndexOutOfBoundsException("Invalid block index: " + blockIndex + ", total blocks: " + signatures.size());
    }

    /**
     * Estimated wire size of the signature table in bytes (approx 24 bytes per block: 4 bytes roll + 16 bytes MD5 + 4 bytes index).
     */
    public long estimatedWireSizeBytes() {
        return (long) signatures.size() * (4 + 16 + 4);
    }
}
