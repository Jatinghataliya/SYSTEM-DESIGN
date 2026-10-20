package com.example.consistenthashing;

import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Consistent hash ring with virtual nodes and weights.
 *
 * The ring is the whole range of a signed 64-bit long, held in a sorted TreeMap
 * (ring position -> physical node). A key belongs to the first position at or after its own
 * hash, wrapping around to the lowest position when there is none. Lookup is O(log V), where V
 * is the total number of virtual nodes.
 *
 * Each node is placed {@code baseVirtualNodes * weight} times, at hash("node#i").
 *
 * Not thread-safe. For concurrent use, build a new ring on membership change and swap a volatile
 * reference to it, so readers never see a half-updated ring.
 */
public final class ConsistentHashRing implements NodeLocator {

    private final int baseVirtualNodes;
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final Map<String, Integer> weights = new HashMap<>();
    private final List<String> insertionOrder = new ArrayList<>();

    public ConsistentHashRing(int baseVirtualNodes) {
        if (baseVirtualNodes < 1) {
            throw new IllegalArgumentException("baseVirtualNodes must be >= 1");
        }
        this.baseVirtualNodes = baseVirtualNodes;
    }

    @Override
    public String name() {
        return "Ring (" + baseVirtualNodes + " vnode" + (baseVirtualNodes == 1 ? "" : "s") + ")";
    }

    @Override
    public void addNode(String node) {
        addNode(node, 1);
    }

    /** A node with weight 2 gets twice as many ring positions, so about twice the keys. */
    public void addNode(String node, int weight) {
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be >= 1");
        }
        if (weights.containsKey(node)) {
            throw new IllegalArgumentException("Node already present: " + node);
        }
        weights.put(node, weight);
        insertionOrder.add(node);
        int points = baseVirtualNodes * weight;
        for (int i = 0; i < points; i++) {
            ring.putIfAbsent(Hashes.hash(node + "#" + i), node); // a 64-bit collision is negligible
        }
    }

    @Override
    public void removeNode(String node) {
        if (weights.remove(node) == null) {
            throw new IllegalArgumentException("Unknown node: " + node);
        }
        insertionOrder.remove(node);
        ring.values().removeIf(owner -> owner.equals(node));
    }

    @Override
    public String locate(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No nodes");
        }
        Map.Entry<Long, String> entry = ring.ceilingEntry(Hashes.hash(key));
        return (entry != null ? entry : ring.firstEntry()).getValue(); // wrap around
    }

    /**
     * The key's owner plus the next distinct physical nodes clockwise, up to {@code count} in total.
     * This is how Dynamo-style stores choose replicas. Distinct matters: with vnodes, the next ring
     * positions often belong to the same machine, which would put two replicas on one box.
     */
    public List<String> locateReplicas(String key, int count) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No nodes");
        }
        long h = Hashes.hash(key);
        Iterable<String> clockwise = Iterables.concat(
                ring.tailMap(h, true).values(),
                ring.headMap(h, false).values());
        Set<String> replicas = new LinkedHashSet<>();
        for (String node : clockwise) {
            replicas.add(node);
            if (replicas.size() == count) {
                break;
            }
        }
        return new ArrayList<>(replicas);
    }

    /** Fraction of the ring each node owns. Shows balance without needing any keys. */
    public Map<String, Double> ownership() {
        Map<String, Double> share = new HashMap<>();
        if (ring.isEmpty()) {
            return share;
        }
        if (ring.size() == 1) {
            share.put(ring.firstEntry().getValue(), 1.0);
            return share;
        }
        final double ringSize = 18446744073709551616.0; // 2^64
        long previous = ring.lastKey();
        for (Map.Entry<Long, String> e : ring.entrySet()) {
            long arc = e.getKey() - previous;                // wraps correctly as unsigned distance
            double length = arc >= 0 ? arc : arc + ringSize;
            share.merge(e.getValue(), length / ringSize, Double::sum);
            previous = e.getKey();
        }
        return share;
    }

    public int ringSize() {
        return ring.size();
    }

    public NavigableMap<Long, String> snapshot() {
        return new TreeMap<>(ring);
    }

    @Override
    public List<String> nodes() {
        return List.copyOf(insertionOrder);
    }
}
