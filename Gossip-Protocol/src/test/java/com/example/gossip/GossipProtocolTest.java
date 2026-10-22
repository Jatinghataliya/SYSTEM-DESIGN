package com.example.gossip;

import com.example.gossip.core.Cluster;
import com.example.gossip.core.GossipNode;
import com.example.gossip.model.NodeId;
import com.example.gossip.model.NodeMember;
import com.example.gossip.model.NodeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class GossipProtocolTest {

    @Test
    @DisplayName("Cluster Formation: All nodes discover each other via Seed node")
    void testClusterSeedDiscovery() {
        Cluster cluster = new Cluster();
        for (int i = 1; i <= 6; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }

        cluster.runTicks(10);

        for (GossipNode node : cluster.getNodes()) {
            assertEquals(6, node.getAllMembers().size(), "Node " + node.getNodeId() + " should know all 6 nodes");
            for (NodeMember member : node.getAllMembers()) {
                assertEquals(NodeStatus.ALIVE, member.getStatus());
            }
        }
    }

    @Test
    @DisplayName("Rumor Mongering: Fast epidemic state propagation across cluster")
    void testRumorMongeringConvergence() {
        Cluster cluster = new Cluster();
        for (int i = 1; i <= 8; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }
        cluster.runTicks(8);

        GossipNode publisher = cluster.getNode("node-1");
        publisher.put("database.primary", "node-3");

        // Advance simulation ticks
        cluster.runTicks(8);

        assertTrue(cluster.isStateConverged("database.primary", "node-3"),
                "All nodes should receive and converge on the updated state");
    }

    @Test
    @DisplayName("SWIM Failure Detector: Node crash escalates to SUSPECT then DEAD")
    void testSWIMFailureDetection() {
        Cluster cluster = new Cluster();
        for (int i = 1; i <= 5; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }
        cluster.runTicks(10);

        GossipNode crashedNode = cluster.getNode("node-4");
        crashedNode.setOnline(false);

        // Advance ticks to trigger failure detector probes and timeouts
        cluster.runTicks(20);

        assertTrue(cluster.isMembershipConverged(crashedNode.getNodeId(), NodeStatus.DEAD),
                "All surviving nodes should declare crashed node-4 as DEAD");
    }

    @Test
    @DisplayName("Suspicion Refutation: Falsely suspected node increments incarnation to refute")
    void testSuspicionRefutation() {
        Cluster cluster = new Cluster();
        GossipNode node1 = cluster.addNode("node-1", 8001);
        GossipNode node2 = cluster.addNode("node-2", 8002);
        cluster.runTicks(5);

        // Manually inject a false suspicion about node-2 into node-1
        NodeMember fakeSuspect = new NodeMember(node2.getNodeId(), NodeStatus.SUSPECT, 5, 1, cluster.getNetwork().getCurrentTick());
        fakeSuspect.setSuspicionStartTick(cluster.getNetwork().getCurrentTick());
        node1.mergeMemberState(fakeSuspect);

        assertEquals(NodeStatus.SUSPECT, node1.getMember(node2.getNodeId()).getStatus());

        // Run ticks: node-2 will communicate with node-1, discover it is suspected, refute it with incarnation bump
        cluster.runTicks(10);

        assertEquals(NodeStatus.ALIVE, node1.getMember(node2.getNodeId()).getStatus(),
                "Node-1 should update node-2 back to ALIVE after refutation");
        assertTrue(node2.getLocalMemberState().getIncarnationNumber() > 1,
                "Node-2 should have bumped its incarnation number");
    }

    @Test
    @DisplayName("Anti-Entropy: Reconciles bidirectional state divergence after partition heal")
    void testAntiEntropyPartitionReconciliation() {
        Cluster cluster = new Cluster();
        GossipNode n1 = cluster.addNode("node-1", 8001);
        GossipNode n2 = cluster.addNode("node-2", 8002);
        GossipNode n3 = cluster.addNode("node-3", 8003);
        GossipNode n4 = cluster.addNode("node-4", 8004);

        cluster.runTicks(10);

        // Create network partition: {node-1, node-2} <---> {node-3, node-4}
        Set<NodeId> partitionA = Set.of(n1.getNodeId(), n2.getNodeId());
        Set<NodeId> partitionB = Set.of(n3.getNodeId(), n4.getNodeId());
        cluster.getNetwork().createPartition(partitionA, partitionB);

        // Mutate states in isolated subnets
        n1.put("k1", "v1_partitionA");
        n3.put("k2", "v2_partitionB");

        cluster.runTicks(5);

        // Verify isolation
        assertNull(n3.get("k1"));
        assertNull(n1.get("k2"));

        // Heal partition
        cluster.getNetwork().healPartitions();

        // Run ticks for Anti-Entropy push-pull sync
        cluster.runTicks(15);

        // Both sides should have reconciled both keys
        assertTrue(cluster.isStateConverged("k1", "v1_partitionA"));
        assertTrue(cluster.isStateConverged("k2", "v2_partitionB"));
    }

    @Test
    @DisplayName("Network Resilience: Gossip achieves convergence under 25% packet drop rate")
    void testPacketDropResilience() {
        Cluster cluster = new Cluster();
        for (int i = 1; i <= 6; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }

        cluster.getNetwork().setDropRate(0.25); // 25% random loss
        cluster.runTicks(15);

        GossipNode publisher = cluster.getNode("node-2");
        publisher.put("resilience.test", "passed");

        // Allow additional rounds to overcome packet drops
        cluster.runTicks(25);

        assertTrue(cluster.isStateConverged("resilience.test", "passed"),
                "Gossip should achieve eventual consistency despite 25% packet loss");
    }
}
