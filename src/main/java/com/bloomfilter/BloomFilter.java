package com.bloomfilter;

import java.util.BitSet;

public class BloomFilter {

    private final int size;
    private final int numHashes;
    private final BitSet bitArray;
    private int numElements;

    /**
     * @param size      Number of bits in the bit array (larger = fewer false positives)
     * @param numHashes Number of hash functions (k)
     */
    public BloomFilter(int size, int numHashes) {
        this.size = size;
        this.numHashes = numHashes;
        this.bitArray = new BitSet(size);
        this.numElements = 0;
    }

    /**
     * Insert an item into the Bloom filter.
     * Hashes the item k times and sets each resulting bit position to 1.
     */
    public void add(String item) {
        int[] positions = HashUtils.getHashPositions(item, numHashes, size);
        for (int pos : positions) {
            bitArray.set(pos);
        }
        numElements++;
    }

    /**
     * Check if an item might be in the Bloom filter.
     *
     * @return true  → POSSIBLY in set (may be a false positive)
     *         false → DEFINITELY NOT in set (no false negatives)
     */
    public boolean contains(String item) {
        int[] positions = HashUtils.getHashPositions(item, numHashes, size);
        for (int pos : positions) {
            if (!bitArray.get(pos)) {
                return false; // At least one bit is 0 → definitely absent
            }
        }
        return true; // All bits are 1 → possibly present
    }

    /**
     * Estimate the current false positive probability using the formula:
     *   P = (1 - e^(-k*n/m))^k
     * where k = numHashes, n = numElements, m = size
     */
    public double falsePositiveRate() {
        double exponent = -((double) numHashes * numElements) / size;
        return Math.pow(1 - Math.exp(exponent), numHashes);
    }

    public int getSize()        { return size; }
    public int getNumHashes()   { return numHashes; }
    public int getNumElements() { return numElements; }

    @Override
    public String toString() {
        return String.format(
            "BloomFilter { size=%d bits, numHashes=%d, elements=%d, fp_rate=%.6f%% }",
            size, numHashes, numElements, falsePositiveRate() * 100
        );
    }
}
