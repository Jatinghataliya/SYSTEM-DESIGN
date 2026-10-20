package com.example.consistenthashing;

import java.util.ArrayList;
import java.util.List;

/**
 * Rendezvous (highest random weight) hashing.
 *
 * For a key, score every node with hash(node + key) and pick the highest. No ring, no vnodes.
 * Removing a node only moves that node's keys, and a new node only takes keys it now scores
 * highest for, the same minimal-movement property as the ring. Cost: O(N) per lookup.
 */
public final class RendezvousLocator implements NodeLocator {

    private final List<String> nodes = new ArrayList<>();

    @Override
    public String name() {
        return "Rendezvous";
    }

    @Override
    public void addNode(String node) {
        if (nodes.contains(node)) {
            throw new IllegalArgumentException("Node already present: " + node);
        }
        nodes.add(node);
    }

    @Override
    public void removeNode(String node) {
        if (!nodes.remove(node)) {
            throw new IllegalArgumentException("Unknown node: " + node);
        }
    }

    @Override
    public String locate(String key) {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("No nodes");
        }
        String best = null;
        long bestScore = Long.MIN_VALUE;
        for (String node : nodes) {
            long score = Hashes.hash(node + "|" + key);
            if (best == null || score > bestScore) {
                best = node;
                bestScore = score;
            }
        }
        return best;
    }

    @Override
    public List<String> nodes() {
        return List.copyOf(nodes);
    }
}
