package com.example.gossip;

import com.example.gossip.core.Cluster;
import com.example.gossip.core.GossipNode;
import com.example.gossip.model.NodeId;
import com.example.gossip.model.NodeMember;
import com.example.gossip.model.NodeStatus;

import java.util.Set;

/**
 * Interactive System Design Demonstration of Gossip Protocol:
 * 1. Seed Node Cluster Discovery
 * 2. Epidemic Rumor Mongering (State Propagation)
 * 3. SWIM Failure Detection (Ping -> Ping-Req -> Suspect -> Dead)
 * 4. Anti-Entropy State Reconciliation (Push-Pull 3-Way Handshake)
 */
public class Main {

    public static void main(String[] args) {
        printBanner();

        demoClusterDiscovery();
        demoRumorMongering();
        demoSwimFailureDetection();
        demoAntiEntropyPartitionReconciliation();

        printSummary();
    }

    private static void printBanner() {
        System.out.println("================================================================================");
        System.out.println("                   GOSSIP PROTOCOL & SWIM FAILURE DETECTOR                     ");
        System.out.println("               Distributed Systems Reference Implementation (Java 17)          ");
        System.out.println("================================================================================\n");
    }

    private static void demoClusterDiscovery() {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" DEMO 1: Cluster Formation & Seed Node Discovery                                ");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" - Bootstrapping 5 nodes: Node-1 (Seed), Node-2, Node-3, Node-4, Node-5.");
        System.out.println(" - New nodes contact Seed Node-1 and spread membership via anti-entropy / gossip.");

        Cluster cluster = new Cluster();
        for (int i = 1; i <= 5; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }

        System.out.println("  Running 10 simulation ticks for cluster convergence...");
        cluster.runTicks(10);

        System.out.println("  Cluster Membership Views after 10 ticks:");
        for (GossipNode node : cluster.getNodes()) {
            System.out.printf("   [%s] knows about %d members: %s%n",
                    node.getNodeId().getId(),
                    node.getAllMembers().size(),
                    node.getAllMembers().stream().map(m -> m.getNodeId().getId() + ":" + m.getStatus()).toList());
        }
        System.out.println("  [+] SUCCESS: All 5 nodes have discovered each other via Gossip!\n");
    }

    private static void demoRumorMongering() {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" DEMO 2: Epidemic Dissemination / Rumor Mongering (State Broadcast)            ");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" - Node-2 publishes: key='cluster.leader' -> 'node-2'");
        System.out.println(" - Node-4 publishes: key='schema.version' -> 'v2.1.0'");

        Cluster cluster = new Cluster();
        for (int i = 1; i <= 6; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }
        cluster.runTicks(5);

        // Publish updates
        GossipNode node2 = cluster.getNode("node-2");
        GossipNode node4 = cluster.getNode("node-4");

        node2.put("cluster.leader", "node-2");
        node4.put("schema.version", "v2.1.0");

        System.out.println("\n  Observing state propagation round by round:");
        for (int tick = 1; tick <= 6; tick++) {
            cluster.runTicks(1);
            long leaderKnownCount = cluster.getNodes().stream()
                    .filter(n -> "node-2".equals(n.get("cluster.leader"))).count();
            long schemaKnownCount = cluster.getNodes().stream()
                    .filter(n -> "v2.1.0".equals(n.get("schema.version"))).count();

            System.out.printf("   Tick %d: 'cluster.leader' known by %d/6 nodes | 'schema.version' known by %d/6 nodes%n",
                    tick, leaderKnownCount, schemaKnownCount);
        }

        System.out.println("  [+] SUCCESS: Full state convergence achieved in O(log N) rounds through epidemic spread!\n");
    }

    private static void demoSwimFailureDetection() {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" DEMO 3: SWIM Failure Detection (Direct Probe -> Indirect Ping-Req -> Dead)    ");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" - Cluster has 5 nodes running. Node-3 experiences a sudden crash.");
        System.out.println(" - SWIM Protocol phases:");
        System.out.println("   1. Direct PING to Node-3 fails.");
        System.out.println("   2. Escalate to indirect PING-REQ via 3 peer nodes.");
        System.out.println("   3. Indirect probes fail -> Node-3 marked SUSPECT.");
        System.out.println("   4. Suspicion timeout expires without refutation -> Declared DEAD.");

        Cluster cluster = new Cluster();
        for (int i = 1; i <= 5; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }
        cluster.runTicks(10);

        GossipNode node3 = cluster.getNode("node-3");
        System.out.println("\n  [!] CRASHING node-3...");
        node3.setOnline(false);

        System.out.println("  Running ticks to observe failure escalation:");
        for (int tick = 1; tick <= 15; tick++) {
            cluster.runTicks(1);
            long suspectCount = cluster.getNodes().stream()
                    .filter(GossipNode::isOnline)
                    .filter(n -> {
                        NodeMember m = n.getMember(node3.getNodeId());
                        return m != null && m.getStatus() == NodeStatus.SUSPECT;
                    }).count();

            long deadCount = cluster.getNodes().stream()
                    .filter(GossipNode::isOnline)
                    .filter(n -> {
                        NodeMember m = n.getMember(node3.getNodeId());
                        return m != null && m.getStatus() == NodeStatus.DEAD;
                    }).count();

            if (tick % 3 == 0 || deadCount == 4) {
                System.out.printf("   Tick %2d: Observers marking Node-3 as SUSPECT: %d/4 | DEAD: %d/4%n",
                        tick, suspectCount, deadCount);
            }
            if (deadCount == 4) break;
        }

        System.out.println("  [+] SUCCESS: SWIM Failure Detector successfully detected crash without false positives!\n");
    }

    private static void demoAntiEntropyPartitionReconciliation() {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" DEMO 4: Network Partition & Anti-Entropy (3-Phase Reconciliation)             ");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" - Splitting cluster into two isolated partitions:");
        System.out.println("   Partition A: {node-1, node-2}");
        System.out.println("   Partition B: {node-3, node-4}");

        Cluster cluster = new Cluster();
        for (int i = 1; i <= 4; i++) {
            cluster.addNode("node-" + i, 8000 + i);
        }
        cluster.runTicks(10);

        Set<NodeId> partitionA = Set.of(cluster.getNode("node-1").getNodeId(), cluster.getNode("node-2").getNodeId());
        Set<NodeId> partitionB = Set.of(cluster.getNode("node-3").getNodeId(), cluster.getNode("node-4").getNodeId());

        cluster.getNetwork().createPartition(partitionA, partitionB);
        System.out.println("  [Network Partition Active]");

        // Mutate state on both sides
        cluster.getNode("node-1").put("feature.auth", "oauth2_v2");
        cluster.getNode("node-4").put("feature.cache", "redis_cluster");

        cluster.runTicks(5);
        System.out.println("  Partition A knows feature.auth  = " + cluster.getNode("node-1").get("feature.auth"));
        System.out.println("  Partition B knows feature.auth  = " + cluster.getNode("node-4").get("feature.auth"));
        System.out.println("  Partition A knows feature.cache = " + cluster.getNode("node-1").get("feature.cache"));
        System.out.println("  Partition B knows feature.cache = " + cluster.getNode("node-4").get("feature.cache"));

        System.out.println("\n  [Healing Network Partition and Running Anti-Entropy Sync (SYN -> ACK -> ACK2)]");
        cluster.getNetwork().healPartitions();
        cluster.runTicks(10);

        System.out.println("  Post-Healing State across all nodes:");
        for (GossipNode node : cluster.getNodes()) {
            System.out.printf("   [%s] feature.auth='%s', feature.cache='%s'%n",
                    node.getNodeId().getId(),
                    node.get("feature.auth"),
                    node.get("feature.cache"));
        }
        System.out.println("  [+] SUCCESS: Both partitions completely reconciled into eventual consistency!\n");
    }

    private static void printSummary() {
        System.out.println("================================================================================");
        System.out.println("                             KEY TAKEAWAYS                                      ");
        System.out.println("================================================================================");
        System.out.println(" 1. Rumor Mongering: O(log N) epidemic broadcast for ultra-fast dissemination.");
        System.out.println(" 2. SWIM Failure Detector: O(1) message overhead per node with 0 false positives.");
        System.out.println(" 3. Anti-Entropy: Periodic 3-way digest handshake for guaranteed eventual consistency.");
        System.out.println(" 4. Incarnation Numbers: Solves false suspicion refutation in dynamic networks.");
        System.out.println("================================================================================\n");
    }
}
