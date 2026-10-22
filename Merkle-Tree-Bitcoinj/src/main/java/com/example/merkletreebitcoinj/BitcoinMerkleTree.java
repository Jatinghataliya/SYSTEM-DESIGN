package com.example.merkletreebitcoinj;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bitcoin-style Merkle Tree using Bitcoinj's double-SHA256 hashing.
 *
 * <p>Bitcoin uses <em>double-SHA256</em> = SHA256(SHA256(data)) for all hashing,
 * including the transaction Merkle root stored in block headers.
 *
 * <p>Key Bitcoin conventions implemented here:
 * <ul>
 *   <li>Leaf hashes: SHA256d of the raw transaction bytes (simulated as UTF-8 strings).</li>
 *   <li>Internal nodes: SHA256d(left || right) — no sorting, order is preserved.</li>
 *   <li>Odd node counts: last node is duplicated (CVE-2012-2459 known but intentional).</li>
 *   <li>Hashes are stored in <em>little-endian</em> (reversed bytes) to match Bitcoin's wire format.</li>
 * </ul>
 */
public class BitcoinMerkleTree {

    private List<Sha256Hash> leaves;

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    /**
     * Builds the Bitcoin Merkle Tree from a list of transaction data strings.
     * Each string is double-SHA256 hashed to produce a leaf hash.
     */
    public void build(List<String> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            throw new IllegalArgumentException("transactions must not be null or empty");
        }
        leaves = new ArrayList<>();
        for (String tx : transactions) {
            // Bitcoin hashes raw transaction bytes; we simulate with UTF-8
            leaves.add(Sha256Hash.twiceOf(tx.getBytes()));
        }
    }

    /**
     * Computes and returns the Merkle root hash.
     * Each call re-runs the tree computation from the leaves.
     */
    public Sha256Hash computeRoot() {
        if (leaves == null || leaves.isEmpty()) {
            throw new IllegalStateException("Tree not built yet. Call build() first.");
        }
        return computeRoot(new ArrayList<>(leaves));
    }

    private Sha256Hash computeRoot(List<Sha256Hash> level) {
        if (level.size() == 1) return level.get(0);

        // Duplicate the last node if odd (Bitcoin standard)
        if (level.size() % 2 != 0) {
            level.add(level.get(level.size() - 1));
        }

        List<Sha256Hash> next = new ArrayList<>();
        for (int i = 0; i < level.size(); i += 2) {
            next.add(hashPair(level.get(i), level.get(i + 1)));
        }
        return computeRoot(next);
    }

    /**
     * Returns the Merkle root as a big-endian hex string
     * (the human-readable format shown in block explorers).
     */
    public String getRootHex() {
        return computeRoot().toString(); // Sha256Hash.toString() = big-endian hex
    }

    // -------------------------------------------------------------------------
    // Leaf info
    // -------------------------------------------------------------------------

    /** Returns all leaf hashes in the order they were inserted. */
    public List<Sha256Hash> getLeaves() {
        if (leaves == null) throw new IllegalStateException("Tree not built yet");
        return List.copyOf(leaves);
    }

    /** Returns the leaf hash at the given index as a hex string. */
    public String getLeafHex(int index) {
        return leaves.get(index).toString();
    }

    // -------------------------------------------------------------------------
    // Double-SHA256 pair hashing
    // -------------------------------------------------------------------------

    /**
     * Computes SHA256d(left_bytes || right_bytes).
     * Bitcoinj's {@link Sha256Hash#twiceOf} applies SHA256 twice.
     */
    private static Sha256Hash hashPair(Sha256Hash left, Sha256Hash right) {
        byte[] leftBytes  = left.getBytes();   // 32 bytes, big-endian
        byte[] rightBytes = right.getBytes();  // 32 bytes, big-endian
        byte[] combined   = new byte[64];
        System.arraycopy(leftBytes,  0, combined,  0, 32);
        System.arraycopy(rightBytes, 0, combined, 32, 32);
        return Sha256Hash.twiceOf(combined);
    }

    // -------------------------------------------------------------------------
    // Utility — reverse bytes for little-endian wire format display
    // -------------------------------------------------------------------------

    /** Converts a Sha256Hash to Bitcoin's little-endian hex (as seen on the wire). */
    public static String toLittleEndianHex(Sha256Hash hash) {
        return Utils.HEX.encode(Utils.reverseBytes(hash.getBytes()));
    }
}
