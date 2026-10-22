package com.merkletree;

/**
 * Represents a single node in a Merkle Tree.
 * Each node holds a SHA-256 hash. Leaf nodes hash actual data;
 * internal nodes hash the concatenation of their children's hashes.
 */
public class MerkleNode {

    private final String hash;
    private final MerkleNode left;
    private final MerkleNode right;
    private final String data; // non-null only for leaf nodes

    /** Leaf node constructor — hashes raw data. */
    public MerkleNode(String data) {
        this.data = data;
        this.left = null;
        this.right = null;
        this.hash = HashUtils.sha256(data);
    }

    /** Internal node constructor — hashes the concatenation of child hashes. */
    public MerkleNode(MerkleNode left, MerkleNode right) {
        this.data = null;
        this.left = left;
        this.right = right;
        this.hash = HashUtils.sha256(left.hash + right.hash);
    }

    public String getHash()       { return hash; }
    public MerkleNode getLeft()   { return left; }
    public MerkleNode getRight()  { return right; }
    public String getData()       { return data; }
    public boolean isLeaf()       { return left == null && right == null; }

    @Override
    public String toString() {
        return isLeaf()
                ? "Leaf[data=" + data + ", hash=" + hash.substring(0, 8) + "...]"
                : "Node[hash=" + hash.substring(0, 8) + "...]";
    }
}
