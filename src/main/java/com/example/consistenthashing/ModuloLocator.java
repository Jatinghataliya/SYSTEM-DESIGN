package com.example.consistenthashing;

import java.util.ArrayList;
import java.util.List;

/**
 * The naive approach: node = hash(key) % N. Perfectly even and very fast, but changing N changes
 * the answer for almost every key. This is the baseline that consistent hashing improves on.
 */
public final class ModuloLocator implements NodeLocator {

    private final List<String> nodes = new ArrayList<>();

    @Override
    public String name() {
        return "Modulo";
    }

    @Override
    public void addNode(String node) {
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
        return nodes.get(Math.floorMod(Hashes.hash(key), nodes.size()));
    }

    @Override
    public List<String> nodes() {
        return List.copyOf(nodes);
    }
}
