package com.example.gossip.core;

import com.example.gossip.model.NodeId;
import com.example.gossip.model.NodeMember;
import com.example.gossip.model.NodeStatus;
import com.example.gossip.network.NetworkSimulator;

import java.util.*;

/**
 * Cluster orchestrator for managing simulated topologies and measuring convergence.
 */
public class Cluster {
    private final NetworkSimulator network = new NetworkSimulator();
    private final Map<NodeId, GossipNode> nodes = new LinkedHashMap<>();
    private final List<NodeId> seedNodes = new ArrayList<>();

    public Cluster() {
    }

    public GossipNode addNode(String id, int port) {
        NodeId nodeId = NodeId.of(id, port);
        GossipNode node = new GossipNode(nodeId);
        nodes.put(nodeId, node);
        network.registerNode(node);

        if (seedNodes.isEmpty()) {
            seedNodes.add(nodeId);
        } else {
            // Join via initial seed node
            node.join(seedNodes.get(0));
        }
        return node;
    }

    public void addSeedNode(NodeId seedNode) {
        if (!seedNodes.contains(seedNode)) {
            seedNodes.add(seedNode);
        }
    }

    public NetworkSimulator getNetwork() {
        return network;
    }

    public GossipNode getNode(String id) {
        for (Map.Entry<NodeId, GossipNode> entry : nodes.entrySet()) {
            if (entry.getKey().getId().equals(id)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Collection<GossipNode> getNodes() {
        return nodes.values();
    }

    public void runTicks(int ticks) {
        network.runTicks(ticks);
    }

    /**
     * Checks if all online nodes have reached consensus on application state key.
     */
    public boolean isStateConverged(String key, String expectedValue) {
        for (GossipNode node : nodes.values()) {
            if (node.isOnline()) {
                String val = node.get(key);
                if (!Objects.equals(val, expectedValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks if all online nodes agree on the status of a specific target node.
     */
    public boolean isMembershipConverged(NodeId target, NodeStatus expectedStatus) {
        for (GossipNode node : nodes.values()) {
            if (node.isOnline() && !node.getNodeId().equals(target)) {
                NodeMember member = node.getMember(target);
                if (member == null || member.getStatus() != expectedStatus) {
                    return false;
                }
            }
        }
        return true;
    }

    public void printMembershipTable() {
        System.out.println("\n" + "=".repeat(90));
        System.out.printf("%-12s | %-12s | %-10s | %-8s | %-8s | %-20s%n",
                "Observer", "Peer", "Status", "Heartbeat", "Incarnation", "App State");
        System.out.println("-".repeat(90));

        for (GossipNode observer : nodes.values()) {
            if (!observer.isOnline()) continue;
            for (NodeMember member : observer.getAllMembers()) {
                System.out.printf("%-12s | %-12s | %-10s | %-8d | %-8d | %-20s%n",
                        observer.getNodeId().getId(),
                        member.getNodeId().getId(),
                        member.getStatus(),
                        member.getHeartbeatSequence(),
                        member.getIncarnationNumber(),
                        member.getApplicationState());
            }
            System.out.println("-".repeat(90));
        }
        System.out.println("=".repeat(90) + "\n");
    }
}
