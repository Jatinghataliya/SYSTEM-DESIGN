package com.example.gossip.network;

import com.example.gossip.core.GossipNode;
import com.example.gossip.model.Message;
import com.example.gossip.model.NodeId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory deterministic and asynchronous network simulator.
 * Supports configurable packet drop rates, network partitions, and latency simulation.
 */
public class NetworkSimulator implements NetworkChannel {
    private final Map<NodeId, GossipNode> nodes = new ConcurrentHashMap<>();
    private final Queue<Envelope> inFlightMessages = new ConcurrentLinkedQueue<>();
    private final Set<Set<NodeId>> partitions = Collections.synchronizedSet(new HashSet<>());
    private final AtomicLong currentTick = new AtomicLong(0);

    private double dropRate = 0.0;
    private final Random random = new Random(42);

    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalDelivered = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);

    private static class Envelope {
        final Message message;
        final long deliverAtTick;

        Envelope(Message message, long deliverAtTick) {
            this.message = message;
            this.deliverAtTick = deliverAtTick;
        }
    }

    public void registerNode(GossipNode node) {
        nodes.put(node.getNodeId(), node);
        node.setNetworkChannel(this);
    }

    public void unregisterNode(NodeId nodeId) {
        nodes.remove(nodeId);
    }

    public GossipNode getNode(NodeId nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<GossipNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public void setDropRate(double dropRate) {
        this.dropRate = Math.max(0.0, Math.min(1.0, dropRate));
    }

    public void createPartition(Set<NodeId> partitionA, Set<NodeId> partitionB) {
        partitions.add(new HashSet<>(partitionA));
        partitions.add(new HashSet<>(partitionB));
    }

    public void healPartitions() {
        partitions.clear();
    }

    private boolean isPartitioned(NodeId from, NodeId to) {
        if (partitions.isEmpty()) {
            return false;
        }
        for (Set<NodeId> part : partitions) {
            if (part.contains(from) && !part.contains(to)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void send(Message message) {
        totalSent.incrementAndGet();

        // Check if message is dropped by drop rate
        if (dropRate > 0.0 && random.nextDouble() < dropRate) {
            totalDropped.incrementAndGet();
            return;
        }

        // Check network partition
        if (isPartitioned(message.getSender(), message.getReceiver())) {
            totalDropped.incrementAndGet();
            return;
        }

        // Check destination node exists and is not offline
        GossipNode targetNode = nodes.get(message.getReceiver());
        if (targetNode == null || !targetNode.isOnline()) {
            totalDropped.incrementAndGet();
            return;
        }

        long deliverTick = currentTick.get() + 1;
        inFlightMessages.add(new Envelope(message, deliverTick));
    }

    @Override
    public long getCurrentTick() {
        return currentTick.get();
    }

    /**
     * Executes one clock tick across the network and all registered nodes.
     */
    public synchronized void tick() {
        long tick = currentTick.incrementAndGet();

        // 1. Deliver eligible in-flight messages
        List<Envelope> remaining = new ArrayList<>();
        while (!inFlightMessages.isEmpty()) {
            Envelope env = inFlightMessages.poll();
            if (env.deliverAtTick <= tick) {
                GossipNode receiverNode = nodes.get(env.message.getReceiver());
                if (receiverNode != null && receiverNode.isOnline()) {
                    receiverNode.receiveMessage(env.message);
                    totalDelivered.incrementAndGet();
                } else {
                    totalDropped.incrementAndGet();
                }
            } else {
                remaining.add(env);
            }
        }
        inFlightMessages.addAll(remaining);

        // 2. Trigger periodic tick on all online nodes
        for (GossipNode node : nodes.values()) {
            if (node.isOnline()) {
                node.onTick(tick);
            }
        }
    }

    /**
     * Run simulation for N ticks.
     */
    public void runTicks(int count) {
        for (int i = 0; i < count; i++) {
            tick();
        }
    }

    public long getTotalSent() {
        return totalSent.get();
    }

    public long getTotalDelivered() {
        return totalDelivered.get();
    }

    public long getTotalDropped() {
        return totalDropped.get();
    }
}
