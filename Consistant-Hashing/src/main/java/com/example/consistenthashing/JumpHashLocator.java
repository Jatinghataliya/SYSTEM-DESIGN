package com.example.consistenthashing;

import com.google.common.hash.Hashing;

import java.util.ArrayList;
import java.util.List;

/**
 * Jump consistent hash, using Guava's {@link Hashing#consistentHash(long, int)}.
 *
 * Very fast, no memory, near-perfect balance, and adding a bucket moves only about 1/(N+1) of the
 * keys. The catch: buckets are numbered 0..N-1, so you can only add or remove the LAST one. It
 * cannot drop an arbitrary node, so it suits numbered shards that only grow, not servers that
 * fail in the middle.
 */
public final class JumpHashLocator implements NodeLocator {

    private final List<String> buckets = new ArrayList<>();

    @Override
    public String name() {
        return "Jump hash (Guava)";
    }

    @Override
    public void addNode(String node) {
        buckets.add(node);
    }

    @Override
    public void removeNode(String node) {
        if (buckets.isEmpty() || !buckets.get(buckets.size() - 1).equals(node)) {
            throw new UnsupportedOperationException(
                    "Jump hash can only remove the last bucket, not: " + node);
        }
        buckets.remove(buckets.size() - 1);
    }

    @Override
    public String locate(String key) {
        if (buckets.isEmpty()) {
            throw new IllegalStateException("No nodes");
        }
        return buckets.get(Hashing.consistentHash(Hashes.hash(key), buckets.size()));
    }

    @Override
    public List<String> nodes() {
        return List.copyOf(buckets);
    }
}
