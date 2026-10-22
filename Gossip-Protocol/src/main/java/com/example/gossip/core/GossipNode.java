package com.example.gossip.core;

import com.example.gossip.model.*;
import com.example.gossip.network.NetworkChannel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core Gossip Node implementation.
 * Manages membership, state storage, rumor propagation, failure detection, and anti-entropy.
 */
public class GossipNode {
    private final NodeId nodeId;
    private final Map<NodeId, NodeMember> membershipTable = new ConcurrentHashMap<>();
    private final Map<String, String> localAppState = new ConcurrentHashMap<>();
    private final Map<String, Long> localStateVersions = new ConcurrentHashMap<>();

    private final AtomicLong heartbeatSequence = new AtomicLong(1);
    private final AtomicLong incarnationNumber = new AtomicLong(1);
    private final AtomicLong sequenceGenerator = new AtomicLong(1);

    private final AtomicBoolean isOnline = new AtomicBoolean(true);
    private NetworkChannel networkChannel;

    private final FailureDetector failureDetector;
    private final RumorMongerEngine rumorEngine;
    private final AntiEntropyEngine antiEntropyEngine;

    private final int antiEntropyIntervalTicks;
    private long lastAntiEntropyTick = 0;

    public GossipNode(NodeId nodeId) {
        this(nodeId, 3, 2, 4, 8, 2, 5, 5);
    }

    public GossipNode(NodeId nodeId,
                      int indirectProbeCount,
                      long directAckTimeoutTicks,
                      long indirectAckTimeoutTicks,
                      long suspicionTimeoutTicks,
                      int rumorFanout,
                      int rumorInfectionRounds,
                      int antiEntropyIntervalTicks) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.antiEntropyIntervalTicks = antiEntropyIntervalTicks;

        // Initialize self in membership table
        NodeMember selfMember = new NodeMember(nodeId, NodeStatus.ALIVE, heartbeatSequence.get(), incarnationNumber.get(), 0);
        membershipTable.put(nodeId, selfMember);

        this.failureDetector = new FailureDetector(this, indirectProbeCount, directAckTimeoutTicks, indirectAckTimeoutTicks, suspicionTimeoutTicks);
        this.rumorEngine = new RumorMongerEngine(this, rumorFanout, rumorInfectionRounds);
        this.antiEntropyEngine = new AntiEntropyEngine(this);
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public void setNetworkChannel(NetworkChannel channel) {
        this.networkChannel = channel;
    }

    public boolean isOnline() {
        return isOnline.get();
    }

    public void setOnline(boolean online) {
        this.isOnline.set(online);
    }

    public long nextSequence() {
        return sequenceGenerator.incrementAndGet();
    }

    /**
     * Join cluster via a seed node by initiating anti-entropy.
     */
    public void join(NodeId seedNode) {
        if (!seedNode.equals(nodeId)) {
            membershipTable.putIfAbsent(seedNode, new NodeMember(seedNode, NodeStatus.ALIVE, 0, 0, 0));
            if (networkChannel != null) {
                antiEntropyEngine.initiateAntiEntropy(networkChannel);
            }
        }
    }

    /**
     * Put or update application key-value state and broadcast via Rumor Mongering.
     */
    public void put(String key, String value) {
        long version = localStateVersions.compute(key, (k, old) -> old == null ? 1L : old + 1);
        localAppState.put(key, value);

        // Update local member representation
        NodeMember self = membershipTable.get(nodeId);
        if (self != null) {
            self.putState(key, value, version);
        }

        // Spread rumor
        long currentTick = networkChannel != null ? networkChannel.getCurrentTick() : 0;
        rumorEngine.broadcastNewRumor(key, value, version, currentTick);
    }

    public String get(String key) {
        return localAppState.get(key);
    }

    public Long getVersion(String key) {
        return localStateVersions.get(key);
    }

    public Map<String, String> getAllLocalState() {
        return Collections.unmodifiableMap(new HashMap<>(localAppState));
    }

    /**
     * Periodic tick driven by network/timer.
     */
    public synchronized void onTick(long currentTick) {
        if (!isOnline.get()) {
            return;
        }

        // 1. Advance heartbeat sequence
        long hb = heartbeatSequence.incrementAndGet();
        NodeMember self = membershipTable.get(nodeId);
        if (self != null) {
            self.setHeartbeatSequence(hb);
            self.setLastUpdatedTick(currentTick);
        }

        // 2. Failure Detection tick
        failureDetector.onTick(currentTick, networkChannel);

        // 3. Rumor Mongering tick
        rumorEngine.onTick(currentTick, networkChannel);

        // 4. Periodic Anti-Entropy sync tick
        if (currentTick - lastAntiEntropyTick >= antiEntropyIntervalTicks) {
            antiEntropyEngine.initiateAntiEntropy(networkChannel);
            lastAntiEntropyTick = currentTick;
        }
    }

    /**
     * Process received network messages.
     */
    public synchronized void receiveMessage(Message message) {
        if (!isOnline.get()) {
            return;
        }

        switch (message.getType()) {
            case PING -> {
                // Merge piggybacked member state
                if (message.getMemberState() != null) {
                    mergeMemberState(message.getMemberState());
                }
                // Respond with ACK
                Message ack = Message.ack(nodeId, message.getSender(), message.getSequenceNumber(), getLocalMemberState());
                networkChannel.send(ack);
            }
            case ACK -> {
                failureDetector.handleAck(message);
            }
            case PING_REQ -> {
                failureDetector.handlePingReq(message, networkChannel);
            }
            case RUMOR_EVENT -> {
                if (message.getRumor() != null) {
                    rumorEngine.receiveRumor(message.getRumor(), networkChannel.getCurrentTick());
                }
            }
            case DIGEST_SYN -> {
                antiEntropyEngine.handleDigestSyn(message, networkChannel);
            }
            case DIGEST_ACK -> {
                antiEntropyEngine.handleDigestAck(message, networkChannel);
            }
            case DIGEST_ACK2 -> {
                antiEntropyEngine.handleDigestAck2(message);
            }
        }
    }

    public NodeMember getLocalMemberState() {
        NodeMember self = membershipTable.get(nodeId);
        return self != null ? self.copy() : new NodeMember(nodeId, NodeStatus.ALIVE, heartbeatSequence.get(), incarnationNumber.get(), 0);
    }

    public List<NodeMember> getProbingCandidates() {
        List<NodeMember> candidates = new ArrayList<>();
        for (NodeMember m : membershipTable.values()) {
            if (!m.getNodeId().equals(nodeId) && (m.getStatus() == NodeStatus.ALIVE || m.getStatus() == NodeStatus.SUSPECT)) {
                candidates.add(m);
            }
        }
        return candidates;
    }

    public List<NodeMember> getHealthyPeersExcluding(NodeId excludeNodeId) {
        List<NodeMember> peers = new ArrayList<>();
        for (NodeMember m : membershipTable.values()) {
            if (!m.getNodeId().equals(nodeId) && !m.getNodeId().equals(excludeNodeId) && m.getStatus() == NodeStatus.ALIVE) {
                peers.add(m);
            }
        }
        return peers;
    }

    public Collection<NodeMember> getAllMembers() {
        return Collections.unmodifiableCollection(membershipTable.values());
    }

    public NodeMember getMember(NodeId id) {
        return membershipTable.get(id);
    }

    public void markSuspect(NodeId target, long currentTick) {
        NodeMember member = membershipTable.get(target);
        if (member != null && member.getStatus() == NodeStatus.ALIVE) {
            member.setStatus(NodeStatus.SUSPECT);
            member.setSuspicionStartTick(currentTick);
        }
    }

    public void markDead(NodeId target, long currentTick) {
        NodeMember member = membershipTable.get(target);
        if (member != null && member.getStatus() != NodeStatus.DEAD) {
            member.setStatus(NodeStatus.DEAD);
            member.setLastUpdatedTick(currentTick);
        }
    }

    public void markAlive(NodeId target, NodeMember incomingState) {
        if (incomingState != null) {
            mergeMemberState(incomingState);
        } else {
            NodeMember member = membershipTable.get(target);
            if (member != null) {
                member.setStatus(NodeStatus.ALIVE);
                member.setSuspicionStartTick(-1);
            }
        }
    }

    /**
     * Refutation and state merging logic.
     */
    public synchronized void mergeMemberState(NodeMember incoming) {
        if (incoming == null) return;
        NodeId incId = incoming.getNodeId();

        // Refutation: If incoming says WE are SUSPECT/DEAD, refute by incrementing incarnation!
        if (incId.equals(this.nodeId)) {
            if (incoming.getStatus() == NodeStatus.SUSPECT || incoming.getStatus() == NodeStatus.DEAD) {
                if (incoming.getIncarnationNumber() >= this.incarnationNumber.get()) {
                    long newInc = incoming.getIncarnationNumber() + 1;
                    this.incarnationNumber.set(newInc);
                    NodeMember self = membershipTable.get(nodeId);
                    if (self != null) {
                        self.setIncarnationNumber(newInc);
                        self.setStatus(NodeStatus.ALIVE);
                        self.setSuspicionStartTick(-1);
                    }
                    if (networkChannel != null) {
                        antiEntropyEngine.initiateAntiEntropy(networkChannel);
                    }
                }
            }
            return;
        }

        NodeMember existing = membershipTable.get(incId);
        if (existing == null) {
            membershipTable.put(incId, incoming.copy());
            return;
        }

        synchronized (existing) {
            if (incoming.getIncarnationNumber() > existing.getIncarnationNumber()) {
                existing.setIncarnationNumber(incoming.getIncarnationNumber());
                existing.setStatus(incoming.getStatus());
                existing.setHeartbeatSequence(incoming.getHeartbeatSequence());
                if (incoming.getStatus() == NodeStatus.ALIVE) {
                    existing.setSuspicionStartTick(-1);
                }
            } else if (incoming.getIncarnationNumber() == existing.getIncarnationNumber()) {
                if (incoming.getStatus() == NodeStatus.SUSPECT && existing.getStatus() == NodeStatus.ALIVE) {
                    existing.setStatus(NodeStatus.SUSPECT);
                    existing.setSuspicionStartTick(incoming.getSuspicionStartTick());
                } else if (incoming.getStatus() == NodeStatus.DEAD) {
                    existing.setStatus(NodeStatus.DEAD);
                }

                if (incoming.getHeartbeatSequence() > existing.getHeartbeatSequence()) {
                    existing.setHeartbeatSequence(incoming.getHeartbeatSequence());
                }
            }

            // Merge application states
            for (Map.Entry<String, String> stateEntry : incoming.getApplicationState().entrySet()) {
                String k = stateEntry.getKey();
                String v = stateEntry.getValue();
                Long ver = incoming.getStateVersions().get(k);
                if (ver != null) {
                    existing.putState(k, v, ver);
                    applyStateUpdate(k, v, ver);
                }
            }
        }
    }

    public synchronized boolean applyStateUpdate(String key, String value, long version) {
        Long currentVer = localStateVersions.get(key);
        if (currentVer == null || version > currentVer) {
            localAppState.put(key, value);
            localStateVersions.put(key, version);

            NodeMember self = membershipTable.get(nodeId);
            if (self != null) {
                self.putState(key, value, version);
            }
            return true;
        }
        return false;
    }

    public List<GossipDigest> produceGossipDigests() {
        List<GossipDigest> digests = new ArrayList<>();
        for (NodeMember m : membershipTable.values()) {
            digests.add(new GossipDigest(m.getNodeId(), m.getStatus(), m.getHeartbeatSequence(), m.getIncarnationNumber(), m.getMaxStateVersion()));
        }
        return digests;
    }
}
