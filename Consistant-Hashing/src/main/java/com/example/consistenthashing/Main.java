package com.example.consistenthashing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class Main {

    private static final int KEYS = 100_000;

    public static void main(String[] args) {
        List<String> keys = Analysis.keys(KEYS);

        section("1. How the ring works (3 nodes, 1 point each)");
        ringBasics();

        section("2. Adding a 5th node: how many of " + KEYS + " keys move?");
        movementOnAdd(keys);

        section("3. Removing a node: only that node's keys should move");
        movementOnRemove(keys);

        section("4. Virtual nodes: load balance with 10 nodes");
        balanceByVirtualNodes(keys);

        section("5. Weighted nodes (a big machine gets more vnodes)");
        weightedNodes(keys);

        section("6. Replication: owner plus the next distinct nodes clockwise");
        replication();

        section("7. Failure: where does a dead node's load go?");
        failureSpread(keys);

        section("8. Lookup speed (1M lookups, 100 nodes)");
        lookupSpeed();
    }

    // ------------------------------------------------------------------ 1

    private static void ringBasics() {
        ConsistentHashRing ring = new ConsistentHashRing(1);
        ring.addNode("node-A");
        ring.addNode("node-B");
        ring.addNode("node-C");

        System.out.println("Ring positions (hash -> node), in clockwise order from the lowest hash:");
        for (Map.Entry<Long, String> e : ring.snapshot().entrySet()) {
            System.out.printf("  %21d -> %s%n", e.getKey(), e.getValue());
        }
        System.out.println();
        System.out.println("Key lookups (walk clockwise to the first node):");
        for (String key : List.of("user:1", "user:2", "user:3", "order:99", "session:abc")) {
            System.out.printf("  %-12s hash=%21d -> %s%n", key, Hashes.hash(key), ring.locate(key));
        }
    }

    // ------------------------------------------------------------------ 2

    private static void movementOnAdd(List<String> keys) {
        System.out.printf("%-22s %-12s %-14s%n", "Strategy", "Keys moved", "Ideal (1/N)");
        for (Supplier<NodeLocator> factory : List.<Supplier<NodeLocator>>of(
                ModuloLocator::new,
                () -> new ConsistentHashRing(1),
                () -> new ConsistentHashRing(150),
                RendezvousLocator::new,
                JumpHashLocator::new)) {
            NodeLocator locator = factory.get();
            for (int i = 1; i <= 4; i++) locator.addNode("node-" + i);
            Map<String, String> before = Analysis.assignments(locator, keys);
            locator.addNode("node-5");
            Map<String, String> after = Analysis.assignments(locator, keys);
            System.out.printf("%-22s %9.1f %%   %9.1f %%%n",
                    locator.name(), Analysis.movedPercent(before, after), 100.0 / 5);
        }
        System.out.println("Modulo moves about 80%. Rendezvous, jump hash and the 150-vnode ring land near the ideal 20%.");
        System.out.println("The 1-vnode ring is just luck: the new node landed on a tiny arc. Rerun with other names and it can be far above 20%.");
    }

    // ------------------------------------------------------------------ 3

    private static void removeFromList(NodeLocator locator, String node) {
        try {
            locator.removeNode(node);
        } catch (UnsupportedOperationException e) {
            System.out.printf("%-22s cannot remove arbitrary nodes (%s)%n", locator.name(), "only the last bucket");
        }
    }

    private static void movementOnRemove(List<String> keys) {
        System.out.printf("%-22s %-12s %-26s%n", "Strategy", "Keys moved", "Moved from OTHER nodes");
        for (Supplier<NodeLocator> factory : List.<Supplier<NodeLocator>>of(
                ModuloLocator::new,
                () -> new ConsistentHashRing(150),
                RendezvousLocator::new)) {
            NodeLocator locator = factory.get();
            for (int i = 1; i <= 5; i++) locator.addNode("node-" + i);
            Map<String, String> before = Analysis.assignments(locator, keys);
            locator.removeNode("node-3");
            Map<String, String> after = Analysis.assignments(locator, keys);
            System.out.printf("%-22s %9.1f %%   %d%n", locator.name(),
                    Analysis.movedPercent(before, after), Analysis.movedNotFrom(before, after, "node-3"));
        }
        JumpHashLocator jump = new JumpHashLocator();
        for (int i = 1; i <= 5; i++) jump.addNode("node-" + i);
        removeFromList(jump, "node-3");
        System.out.println("A '0' in the last column means no key moved except those owned by the removed node.");
    }

    // ------------------------------------------------------------------ 4

    private static void balanceByVirtualNodes(List<String> keys) {
        System.out.printf("%-22s %-14s %-12s %-12s%n", "Strategy", "Ring points", "Busiest/avg", "Idlest/avg");
        for (int vnodes : new int[]{1, 10, 50, 150, 500}) {
            ConsistentHashRing ring = new ConsistentHashRing(vnodes);
            for (int i = 1; i <= 10; i++) ring.addNode("node-" + i);
            Map<String, Integer> counts = Analysis.countPerNode(Analysis.assignments(ring, keys));
            double avg = KEYS / 10.0;
            int min = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            int minOverall = counts.size() < 10 ? 0 : min; // a node with no keys is missing from counts
            System.out.printf("%-22s %-14d %-12.2f %-12.2f%n", ring.name(), ring.ringSize(),
                    Analysis.peakToAverage(counts, 10, KEYS), minOverall / avg);
        }
        System.out.println("1.00 is perfect. More vnodes pull both columns toward 1.00, at the cost of a bigger ring.");
    }

    // ------------------------------------------------------------------ 5

    private static void weightedNodes(List<String> keys) {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        ring.addNode("small-1", 1);
        ring.addNode("small-2", 1);
        ring.addNode("medium", 2);
        ring.addNode("large", 4);
        Map<String, Integer> counts = Analysis.countPerNode(Analysis.assignments(ring, keys));
        System.out.printf("%-10s %-8s %-10s %-10s%n", "Node", "Weight", "Keys", "Share");
        Map<String, Integer> weight = Map.of("small-1", 1, "small-2", 1, "medium", 2, "large", 4);
        for (String node : ring.nodes()) {
            System.out.printf("%-10s %-8d %-10d %5.1f %%   (target %.1f %%)%n", node, weight.get(node),
                    counts.get(node), 100.0 * counts.get(node) / KEYS, 100.0 * weight.get(node) / 8);
        }
    }

    // ------------------------------------------------------------------ 6

    private static void replication() {
        ConsistentHashRing ring = new ConsistentHashRing(100);
        for (String n : List.of("node-A", "node-B", "node-C", "node-D", "node-E")) ring.addNode(n);
        System.out.println("Replication factor 3. Every replica set holds 3 DIFFERENT physical nodes,");
        System.out.println("even though neighbouring ring positions often belong to the same node:");
        for (String key : List.of("user:1", "user:2", "user:3", "order:99", "session:abc")) {
            System.out.printf("  %-12s -> %s%n", key, ring.locateReplicas(key, 3));
        }
    }

    // ------------------------------------------------------------------ 7

    private static void failureSpread(List<String> keys) {
        System.out.println("10 nodes; node-1 dies. Share of node-1's keys that lands on each survivor:");
        for (int vnodes : new int[]{1, 150}) {
            ConsistentHashRing ring = new ConsistentHashRing(vnodes);
            for (int i = 1; i <= 10; i++) ring.addNode("node-" + i);
            Map<String, String> before = Analysis.assignments(ring, keys);
            ring.removeNode("node-1");
            Map<String, String> after = Analysis.assignments(ring, keys);

            Map<String, Integer> received = new TreeMap<>();
            int orphaned = 0;
            for (Map.Entry<String, String> e : before.entrySet()) {
                if (e.getValue().equals("node-1")) {
                    orphaned++;
                    received.merge(after.get(e.getKey()), 1, Integer::sum);
                }
            }
            int worst = received.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            System.out.printf("  %-18s node-1 owned %d keys, spread over %d survivors, worst survivor took %.0f%%%n",
                    ring.name(), orphaned, received.size(), 100.0 * worst / orphaned);
        }
        System.out.println("With 1 point per node a single neighbour absorbs most of the load. With 150 it is shared.");
    }

    // ------------------------------------------------------------------ 8

    private static void lookupSpeed() {
        int lookups = 1_000_000;
        List<String> keys = Analysis.keys(10_000);
        List<NodeLocator> locators = new ArrayList<>();
        locators.add(new ModuloLocator());
        locators.add(new ConsistentHashRing(150));
        locators.add(new RendezvousLocator());
        locators.add(new JumpHashLocator());
        for (NodeLocator locator : locators) {
            for (int i = 1; i <= 100; i++) locator.addNode("node-" + i);
        }
        System.out.printf("%-22s %-14s%n", "Strategy", "ns per lookup");
        for (NodeLocator locator : locators) {
            for (int i = 0; i < 100_000; i++) locator.locate(keys.get(i % keys.size())); // warm-up
            long t0 = System.nanoTime();
            for (int i = 0; i < lookups; i++) locator.locate(keys.get(i % keys.size()));
            long t1 = System.nanoTime();
            System.out.printf("%-22s %-14.0f%n", locator.name(), (double) (t1 - t0) / lookups);
        }
        System.out.println("Rendezvous is O(N): it scores every node, so it slows down as the cluster grows.");
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
