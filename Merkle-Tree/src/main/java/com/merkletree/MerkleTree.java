package com.merkletree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Merkle Tree implementation.
 *
 * <p>Builds a complete binary tree from a list of data blocks. When the number
 * of leaf nodes is odd, the last leaf is duplicated so that every internal node
 * always has exactly two children.</p>
 *
 * <p>Key operations:</p>
 * <ul>
 *   <li>{@link #build(List)} — construct the tree from data blocks</li>
 *   <li>{@link #getRootHash()} — retrieve the Merkle root</li>
 *   <li>{@link #getProof(int)} — generate an audit/inclusion proof for a leaf</li>
 *   <li>{@link #verify(String, List, String)} — verify a proof against a known root</li>
 * </ul>
 */
public class MerkleTree {

    private MerkleNode root;
    private List<MerkleNode> leaves;

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Builds the Merkle Tree from the provided list of data blocks.
     *
     * @param dataBlocks non-null, non-empty list of raw data strings (e.g. transactions)
     * @throws IllegalArgumentException if the list is null or empty
     */
    public void build(List<String> dataBlocks) {
        if (dataBlocks == null || dataBlocks.isEmpty()) {
            throw new IllegalArgumentException("dataBlocks must not be null or empty");
        }

        // Step 1: Create leaf nodes
        leaves = new ArrayList<>();
        for (String data : dataBlocks) {
            leaves.add(new MerkleNode(data));
        }

        // Step 2: Bottom-up construction
        root = buildTree(new ArrayList<>(leaves));
    }

    private MerkleNode buildTree(List<MerkleNode> nodes) {
        if (nodes.size() == 1) {
            return nodes.get(0);
        }

        // Duplicate the last node if the count is odd (standard Bitcoin-style padding)
        if (nodes.size() % 2 != 0) {
            nodes.add(nodes.get(nodes.size() - 1));
        }

        List<MerkleNode> parentLevel = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i += 2) {
            parentLevel.add(new MerkleNode(nodes.get(i), nodes.get(i + 1)));
        }
        return buildTree(parentLevel);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns the root hash of the Merkle Tree. */
    public String getRootHash() {
        ensureBuilt();
        return root.getHash();
    }

    /** Returns the root node of the Merkle Tree. */
    public MerkleNode getRoot() {
        ensureBuilt();
        return root;
    }

    /** Returns an unmodifiable view of the leaf nodes. */
    public List<MerkleNode> getLeaves() {
        ensureBuilt();
        return Collections.unmodifiableList(leaves);
    }

    // -------------------------------------------------------------------------
    // Merkle Proof (audit proof / inclusion proof)
    // -------------------------------------------------------------------------

    /**
     * Generates a Merkle proof (list of sibling hashes) for the leaf at the
     * given index. The proof can be used to verify that the leaf is part of
     * the tree without revealing any other data.
     *
     * @param leafIndex 0-based index of the leaf
     * @return ordered list of {@link ProofStep} objects (sibling hash + direction)
     */
    public List<ProofStep> getProof(int leafIndex) {
        ensureBuilt();
        if (leafIndex < 0 || leafIndex >= leaves.size()) {
            throw new IndexOutOfBoundsException("leafIndex out of range: " + leafIndex);
        }

        List<ProofStep> proof = new ArrayList<>();
        collectProof(root, leaves.get(leafIndex).getHash(), proof);
        return proof;
    }

    private boolean collectProof(MerkleNode node, String targetHash, List<ProofStep> proof) {
        if (node == null) return false;
        if (node.isLeaf()) return node.getHash().equals(targetHash);

        if (collectProof(node.getLeft(), targetHash, proof)) {
            proof.add(new ProofStep(node.getRight().getHash(), Direction.RIGHT));
            return true;
        }
        if (collectProof(node.getRight(), targetHash, proof)) {
            proof.add(new ProofStep(node.getLeft().getHash(), Direction.LEFT));
            return true;
        }
        return false;
    }

    /**
     * Verifies a Merkle proof.
     *
     * @param leafData    the raw data of the leaf being proven
     * @param proof       the proof steps returned by {@link #getProof(int)}
     * @param expectedRoot the Merkle root to verify against
     * @return {@code true} if the proof is valid
     */
    public static boolean verify(String leafData, List<ProofStep> proof, String expectedRoot) {
        String currentHash = HashUtils.sha256(leafData);
        for (ProofStep step : proof) {
            if (step.getDirection() == Direction.RIGHT) {
                currentHash = HashUtils.sha256(currentHash + step.getSiblingHash());
            } else {
                currentHash = HashUtils.sha256(step.getSiblingHash() + currentHash);
            }
        }
        return currentHash.equals(expectedRoot);
    }

    // -------------------------------------------------------------------------
    // Pretty print
    // -------------------------------------------------------------------------

    /**
     * Prints the tree level-by-level to standard output for debugging.
     */
    public void printTree() {
        ensureBuilt();
        System.out.println("\n=== Merkle Tree ===");
        printNode(root, "", true);
    }

    private void printNode(MerkleNode node, String prefix, boolean isLeft) {
        if (node == null) return;
        System.out.println(prefix + (isLeft ? "├── " : "└── ") + node);
        printNode(node.getLeft(),  prefix + (isLeft ? "│   " : "    "), true);
        printNode(node.getRight(), prefix + (isLeft ? "│   " : "    "), false);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void ensureBuilt() {
        if (root == null) {
            throw new IllegalStateException("Tree has not been built yet. Call build() first.");
        }
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    public enum Direction { LEFT, RIGHT }

    /** A single step in a Merkle proof — the sibling hash and its position. */
    public static class ProofStep {
        private final String siblingHash;
        private final Direction direction;

        public ProofStep(String siblingHash, Direction direction) {
            this.siblingHash = siblingHash;
            this.direction = direction;
        }

        public String getSiblingHash() { return siblingHash; }
        public Direction getDirection() { return direction; }

        @Override
        public String toString() {
            return direction + ":" + HashUtils.shortHash(siblingHash);
        }
    }
}
