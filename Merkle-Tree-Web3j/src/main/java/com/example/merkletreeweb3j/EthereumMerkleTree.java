package com.example.merkletreeweb3j;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ethereum-style Merkle Tree using Web3j's Keccak-256 hashing.
 *
 * <p>Ethereum uses Keccak-256 (NOT standard SHA3-256) for all hashing,
 * including the transaction Merkle root in a block header.
 *
 * <p>This implementation mirrors the Ethereum convention:
 * <ul>
 *   <li>Each leaf is keccak256(data bytes).</li>
 *   <li>Each internal node is keccak256(left || right), where the two
 *       child hashes are sorted before concatenation — this is the
 *       <em>sorted-pair</em> pattern used by OpenZeppelin's MerkleProof.sol.</li>
 *   <li>Odd node counts pad by duplicating the last node.</li>
 * </ul>
 *
 * <p>Run: {@code mvn compile exec:java}
 */
public class EthereumMerkleTree {

    private List<byte[]> leaves;   // keccak256 of each raw data item
    private List<byte[]> tree;     // all nodes stored level-by-level bottom-up

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Builds the Merkle Tree from a list of plain-text data items.
     * Each item is keccak256-hashed before being inserted as a leaf.
     */
    public void build(List<String> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be null or empty");
        }

        leaves = new ArrayList<>();
        for (String item : items) {
            leaves.add(keccak256(item.getBytes(StandardCharsets.UTF_8)));
        }

        tree = new ArrayList<>(leaves);
        buildLevels(new ArrayList<>(leaves));
    }

    private void buildLevels(List<byte[]> level) {
        if (level.size() == 1) return;  // reached root

        if (level.size() % 2 != 0) {
            level.add(level.get(level.size() - 1)); // duplicate last (padding)
        }

        List<byte[]> next = new ArrayList<>();
        for (int i = 0; i < level.size(); i += 2) {
            byte[] parent = hashPair(level.get(i), level.get(i + 1));
            next.add(parent);
            tree.add(parent);
        }
        buildLevels(next);
    }

    // -------------------------------------------------------------------------
    // Root
    // -------------------------------------------------------------------------

    /** Returns the Merkle root as a 0x-prefixed hex string (Ethereum style). */
    public String getRootHex() {
        if (tree == null || tree.isEmpty()) {
            throw new IllegalStateException("Tree not built yet");
        }
        return Numeric.toHexString(tree.get(tree.size() - 1));
    }

    // -------------------------------------------------------------------------
    // Proof generation
    // -------------------------------------------------------------------------

    /**
     * Generates a Merkle proof for the leaf at {@code leafIndex}.
     * The proof is a list of sibling hashes as 0x-prefixed hex strings.
     * Compatible with OpenZeppelin's {@code MerkleProof.verify} on-chain.
     */
    public List<String> getProof(int leafIndex) {
        if (leaves == null) throw new IllegalStateException("Tree not built yet");
        if (leafIndex < 0 || leafIndex >= leaves.size()) {
            throw new IndexOutOfBoundsException("leafIndex out of range: " + leafIndex);
        }

        List<String> proof = new ArrayList<>();
        List<byte[]> level = new ArrayList<>(leaves);
        int idx = leafIndex;

        while (level.size() > 1) {
            if (level.size() % 2 != 0) {
                level.add(level.get(level.size() - 1));
            }
            int siblingIdx = (idx % 2 == 0) ? idx + 1 : idx - 1;
            proof.add(Numeric.toHexString(level.get(siblingIdx)));

            List<byte[]> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                next.add(hashPair(level.get(i), level.get(i + 1)));
            }
            level = next;
            idx /= 2;
        }
        return proof;
    }

    // -------------------------------------------------------------------------
    // Proof verification (static — usable without a tree instance)
    // -------------------------------------------------------------------------

    /**
     * Verifies a Merkle proof.
     *
     * @param leafData     raw data of the leaf
     * @param proof        sibling hashes as 0x-prefixed hex strings
     * @param expectedRoot expected root as 0x-prefixed hex string
     */
    public static boolean verify(String leafData, List<String> proof, String expectedRoot) {
        byte[] current = keccak256(leafData.getBytes(StandardCharsets.UTF_8));
        for (String siblingHex : proof) {
            byte[] sibling = Numeric.hexStringToByteArray(siblingHex);
            current = hashPair(current, sibling);
        }
        return Numeric.toHexString(current).equalsIgnoreCase(expectedRoot);
    }

    // -------------------------------------------------------------------------
    // Keccak-256 helpers
    // -------------------------------------------------------------------------

    /** Web3j's {@link Hash#sha3} is Keccak-256 (not NIST SHA-3). */
    public static byte[] keccak256(byte[] input) {
        return Hash.sha3(input);
    }

    /**
     * Sorted-pair hash: sorts the two 32-byte hashes lexicographically before
     * concatenating and hashing. This makes proofs position-independent, matching
     * OpenZeppelin MerkleProof.sol.
     */
    private static byte[] hashPair(byte[] a, byte[] b) {
        byte[] concat = compare(a, b) <= 0
                ? concat(a, b)
                : concat(b, a);
        return keccak256(concat);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static int compare(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int cmp = Byte.compareUnsigned(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }
}
