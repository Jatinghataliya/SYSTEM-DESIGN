package com.example.consistenthashing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistentHashingTest {

    private static final List<String> KEYS = Analysis.keys(50_000);

    private static ConsistentHashRing ring(int vnodes, int nodes) {
        ConsistentHashRing ring = new ConsistentHashRing(vnodes);
        for (int i = 1; i <= nodes; i++) ring.addNode("node-" + i);
        return ring;
    }

    // ---- basic behaviour ----

    @Test
    void sameKeyAlwaysMapsToSameNode() {
        ConsistentHashRing ring = ring(100, 5);
        for (String key : KEYS.subList(0, 1000)) {
            assertEquals(ring.locate(key), ring.locate(key));
        }
    }

    @Test
    void twoRingsBuiltInDifferentOrderAgree() {
        // Clients that build the ring independently must still agree on placement.
        ConsistentHashRing a = new ConsistentHashRing(100);
        ConsistentHashRing b = new ConsistentHashRing(100);
        for (String n : List.of("n1", "n2", "n3", "n4")) a.addNode(n);
        for (String n : List.of("n4", "n2", "n1", "n3")) b.addNode(n);
        for (String key : KEYS.subList(0, 5000)) {
            assertEquals(a.locate(key), b.locate(key));
        }
    }

    @Test
    void lookupWrapsAroundTheRing() {
        ConsistentHashRing ring = ring(1, 3);
        long highest = ring.snapshot().lastKey();
        long lowest = ring.snapshot().firstKey();
        String owner = ring.snapshot().get(lowest);
        // find a key hashing above the highest ring position: it must wrap to the lowest
        for (int i = 0; i < 1_000_000; i++) {
            String key = "probe-" + i;
            if (Hashes.hash(key) > highest) {
                assertEquals(owner, ring.locate(key));
                return;
            }
        }
        throw new AssertionError("no key hashed above the last ring position");
    }

    @Test
    void emptyRingIsRejected() {
        assertThrows(IllegalStateException.class, () -> new ConsistentHashRing(10).locate("k"));
    }

    @Test
    void duplicateAndUnknownNodesAreRejected() {
        ConsistentHashRing ring = ring(10, 2);
        assertThrows(IllegalArgumentException.class, () -> ring.addNode("node-1"));
        assertThrows(IllegalArgumentException.class, () -> ring.removeNode("ghost"));
    }

    // ---- the core promise: minimal movement ----

    @Test
    void removingANodeMovesOnlyThatNodesKeys() {
        ConsistentHashRing ring = ring(150, 6);
        Map<String, String> before = Analysis.assignments(ring, KEYS);
        ring.removeNode("node-3");
        Map<String, String> after = Analysis.assignments(ring, KEYS);

        assertEquals(0, Analysis.movedNotFrom(before, after, "node-3"),
                "keys on surviving nodes must not move");
        assertTrue(after.values().stream().noneMatch("node-3"::equals));
    }

    @Test
    void addingANodeOnlyMovesKeysToTheNewNode() {
        ConsistentHashRing ring = ring(150, 5);
        Map<String, String> before = Analysis.assignments(ring, KEYS);
        ring.addNode("node-new");
        Map<String, String> after = Analysis.assignments(ring, KEYS);

        for (String key : KEYS) {
            if (!before.get(key).equals(after.get(key))) {
                assertEquals("node-new", after.get(key), "a moved key may only move to the new node");
            }
        }
    }

    @Test
    void ringMovesFarFewerKeysThanModuloWhenGrowingFourToFive() {
        double moduloMoved = movedOnGrow(new ModuloLocator());
        double ringMoved = movedOnGrow(new ConsistentHashRing(150));
        assertTrue(moduloMoved > 75 && moduloMoved < 85, "modulo moved " + moduloMoved);
        assertTrue(ringMoved > 12 && ringMoved < 28, "ring moved " + ringMoved);
    }

    private static double movedOnGrow(NodeLocator locator) {
        for (int i = 1; i <= 4; i++) locator.addNode("node-" + i);
        Map<String, String> before = Analysis.assignments(locator, KEYS);
        locator.addNode("node-5");
        return Analysis.movedPercent(before, Analysis.assignments(locator, KEYS));
    }

    // ---- virtual nodes and weights ----

    @Test
    void moreVirtualNodesGiveBetterBalance() {
        double few = peakToAverage(ring(1, 10));
        double many = peakToAverage(ring(300, 10));
        assertTrue(few > 1.5, "1 vnode should be clearly unbalanced, was " + few);
        assertTrue(many < 1.2, "300 vnodes should be near balanced, was " + many);
        assertTrue(many < few);
    }

    private static double peakToAverage(ConsistentHashRing ring) {
        Map<String, Integer> counts = Analysis.countPerNode(Analysis.assignments(ring, KEYS));
        return Analysis.peakToAverage(counts, ring.nodes().size(), KEYS.size());
    }

    @Test
    void weightedNodeGetsProportionalShare() {
        ConsistentHashRing ring = new ConsistentHashRing(200);
        ring.addNode("small", 1);
        ring.addNode("big", 3);
        Map<String, Integer> counts = Analysis.countPerNode(Analysis.assignments(ring, KEYS));
        double bigShare = counts.get("big") / (double) KEYS.size();
        assertEquals(0.75, bigShare, 0.05);
    }

    @Test
    void ownershipSumsToOne() {
        ConsistentHashRing ring = ring(100, 7);
        double total = ring.ownership().values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, total, 1e-9);
    }

    @Test
    void failureLoadSpreadsAcrossManySurvivorsWithVirtualNodes() {
        ConsistentHashRing ring = ring(150, 10);
        Map<String, String> before = Analysis.assignments(ring, KEYS);
        ring.removeNode("node-1");
        Map<String, String> after = Analysis.assignments(ring, KEYS);

        Set<String> receivers = new java.util.HashSet<>();
        for (String key : KEYS) {
            if (before.get(key).equals("node-1")) receivers.add(after.get(key));
        }
        assertEquals(9, receivers.size(), "every survivor should take a share");
    }

    // ---- replication ----

    @Test
    void replicasAreDistinctPhysicalNodesAndStartWithTheOwner() {
        ConsistentHashRing ring = ring(150, 6);
        for (String key : KEYS.subList(0, 2000)) {
            List<String> replicas = ring.locateReplicas(key, 3);
            assertEquals(3, replicas.size());
            assertEquals(3, Set.copyOf(replicas).size(), "replicas must be distinct nodes: " + replicas);
            assertEquals(ring.locate(key), replicas.get(0));
        }
    }

    @Test
    void replicaCountIsCappedByNodeCount() {
        assertEquals(2, ring(50, 2).locateReplicas("k", 5).size());
    }

    // ---- the other strategies ----

    @Test
    void rendezvousMovesOnlyTheRemovedNodesKeys() {
        RendezvousLocator hrw = new RendezvousLocator();
        for (int i = 1; i <= 6; i++) hrw.addNode("node-" + i);
        Map<String, String> before = Analysis.assignments(hrw, KEYS);
        hrw.removeNode("node-4");
        Map<String, String> after = Analysis.assignments(hrw, KEYS);
        assertEquals(0, Analysis.movedNotFrom(before, after, "node-4"));
    }

    @Test
    void jumpHashGrowsMinimallyButOnlyRemovesTheLastBucket() {
        JumpHashLocator jump = new JumpHashLocator();
        for (int i = 1; i <= 5; i++) jump.addNode("node-" + i);
        assertThrows(UnsupportedOperationException.class, () -> jump.removeNode("node-2"));

        Map<String, String> before = Analysis.assignments(jump, KEYS);
        jump.addNode("node-6");
        double moved = Analysis.movedPercent(before, Analysis.assignments(jump, KEYS));
        assertEquals(100.0 / 6, moved, 2.0);
        jump.removeNode("node-6"); // removing the last one is allowed
        assertEquals(5, jump.nodes().size());
    }

    @Test
    void moduloReshufflesMostKeys() {
        assertTrue(movedOnGrow(new ModuloLocator()) > 70);
    }
}
