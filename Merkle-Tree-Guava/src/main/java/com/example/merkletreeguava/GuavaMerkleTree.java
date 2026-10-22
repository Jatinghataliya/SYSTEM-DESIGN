package com.example.merkletreeguava;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Merkle Tree built with Google Guava's {@link Hashing} API.
 *
 * <p>Guava's key advantage is its pluggable {@link HashFunction} abstraction.
 * The same Merkle Tree code works with any hash function —
 * SHA-256, SHA-512, SHA-384, Murmur3, etc. — just by swapping the
 * {@link HashFunction} at construction time.
 *
 * <p>Conventions:
 * <ul>
 *   <li>Leaf: hashFn(data).</li>
 *   <li>Internal node: hashFn(leftHex + rightHex).</li>
 *   <li>Odd counts: last node duplicated.</li>
 * </ul>
 */
public class GuavaMerkleTree {

    private final HashFunction hashFn;
    private List<HashCode> leaves;

    /**
     * @param hashFn the Guava {@link HashFunction} to use for all hashing
     *               (e.g. {@code Hashing.sha256()}, {@code Hashing.sha512()})
     */
    public GuavaMerkleTree(HashFunction hashFn) {
        this.hashFn = hashFn;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Builds the tree from a list of data strings.
     * Each string is hashed with the configured {@link HashFunction}.
     */
    public void build(List<String> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }
        leaves = new ArrayList<>();
        for (String item : items) {
            leaves.add(hash(item));
        }
    }

    /**
     * Computes and returns the Merkle root as a {@link HashCode}.
     */
    public HashCode computeRoot() {
        if (leaves == null || leaves.isEmpty()) {
            throw new IllegalStateException("Tree not built yet. Call build() first.");
        }
        return computeRoot(new ArrayList<>(leaves));
    }

    private HashCode computeRoot(List<HashCode> level) {
        if (level.size() == 1) return level.get(0);
        if (level.size() % 2 != 0) {
            level.add(level.get(level.size() - 1)); // duplicate last
        }
        List<HashCode> next = new ArrayList<>();
        for (int i = 0; i < level.size(); i += 2) {
            next.add(hashPair(level.get(i), level.get(i + 1)));
        }
        return computeRoot(next);
    }

    /** Returns the Merkle root as a lowercase hex string. */
    public String getRootHex() {
        return computeRoot().toString();
    }

    /** Returns the name of the hash function in use (e.g. "sha-256"). */
    public String getAlgorithmName() {
        return hashFn.toString();
    }

    // -------------------------------------------------------------------------
    // Proof
    // -------------------------------------------------------------------------

    /**
     * Generates an inclusion proof for the leaf at {@code leafIndex}.
     *
     * @return ordered list of sibling {@link HashCode}s
     */
    public List<HashCode> getProof(int leafIndex) {
        if (leaves == null) throw new IllegalStateException("Tree not built yet");
        if (leafIndex < 0 || leafIndex >= leaves.size()) {
            throw new IndexOutOfBoundsException("leafIndex out of range: " + leafIndex);
        }
        List<HashCode> proof = new ArrayList<>();
        collectProof(new ArrayList<>(leaves), leafIndex, proof);
        return proof;
    }

    private void collectProof(List<HashCode> level, int idx, List<HashCode> proof) {
        if (level.size() == 1) return;
        if (level.size() % 2 != 0) level.add(level.get(level.size() - 1));

        int siblingIdx = (idx % 2 == 0) ? idx + 1 : idx - 1;
        proof.add(level.get(siblingIdx));

        List<HashCode> next = new ArrayList<>();
        for (int i = 0; i < level.size(); i += 2) {
            next.add(hashPair(level.get(i), level.get(i + 1)));
        }
        collectProof(next, idx / 2, proof);
    }

    // -------------------------------------------------------------------------
    // Verification (static — usable without a tree instance)
    // -------------------------------------------------------------------------

    /**
     * Verifies a Merkle proof.
     *
     * @param hashFn       the same hash function used to build the tree
     * @param leafData     raw data of the leaf being proven
     * @param leafIndex    0-based index of the leaf (needed to know left/right direction)
     * @param proof        sibling hashes from {@link #getProof}
     * @param expectedRoot the known Merkle root
     */
    public static boolean verify(HashFunction hashFn, String leafData, int leafIndex,
                                  List<HashCode> proof, String expectedRoot) {
        HashCode current = hashFn.hashString(leafData, StandardCharsets.UTF_8);
        int idx = leafIndex;
        for (HashCode sibling : proof) {
            HashCode combined = (idx % 2 == 0)
                    ? hashFn.hashString(current.toString() + sibling.toString(), StandardCharsets.UTF_8)
                    : hashFn.hashString(sibling.toString() + current.toString(), StandardCharsets.UTF_8);
            current = combined;
            idx /= 2;
        }
        return current.toString().equals(expectedRoot);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HashCode hash(String data) {
        return hashFn.hashString(data, StandardCharsets.UTF_8);
    }

    private HashCode hashPair(HashCode left, HashCode right) {
        return hashFn.hashString(left.toString() + right.toString(), StandardCharsets.UTF_8);
    }

    /** Returns an unmodifiable view of the leaf hashes. */
    public List<HashCode> getLeaves() {
        return Collections.unmodifiableList(leaves);
    }
}
