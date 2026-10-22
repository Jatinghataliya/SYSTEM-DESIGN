package com.example.gossip.core;

import com.example.gossip.model.*;
import com.example.gossip.network.NetworkChannel;

import java.util.*;

/**
 * 3-Phase Anti-Entropy State Reconciliation Engine (Push-Pull protocol).
 *
 * Modeled after Apache Cassandra & Amazon Dynamo anti-entropy gossip:
 * 1. Node A sends DIGEST_SYN (Digests of what A knows)
 * 2. Node B replies with DIGEST_ACK (Updates A is missing + requests for what B is missing)
 * 3. Node A replies with DIGEST_ACK2 (Updates B requested)
 */
public class AntiEntropyEngine {
    private final GossipNode localNode;
    private final Random random = new Random();

    public AntiEntropyEngine(GossipNode localNode) {
        this.localNode = localNode;
    }

    public void initiateAntiEntropy(NetworkChannel channel) {
        List<NodeMember> peers = localNode.getHealthyPeersExcluding(localNode.getNodeId());
        if (peers.isEmpty()) {
            return;
        }

        NodeMember target = peers.get(random.nextInt(peers.size()));
        List<GossipDigest> digests = localNode.produceGossipDigests();

        Message syn = Message.digestSyn(localNode.getNodeId(), target.getNodeId(), digests);
        channel.send(syn);
    }

    public void handleDigestSyn(Message synMessage, NetworkChannel channel) {
        List<GossipDigest> remoteDigests = synMessage.getDigests();
        GossipDelta delta = new GossipDelta();

        Map<NodeId, GossipDigest> remoteDigestMap = new HashMap<>();
        for (GossipDigest d : remoteDigests) {
            remoteDigestMap.put(d.getNodeId(), d);
        }

        // Compare local state with remote digest
        for (NodeMember localMember : localNode.getAllMembers()) {
            GossipDigest remoteDigest = remoteDigestMap.get(localMember.getNodeId());

            if (remoteDigest == null) {
                // Remote node has never heard of this member -> Send full member update
                delta.addMemberUpdate(localMember);
                for (Map.Entry<String, String> state : localMember.getApplicationState().entrySet()) {
                    long ver = localMember.getStateVersions().getOrDefault(state.getKey(), 1L);
                    delta.addStateValue(state.getKey(), state.getValue(), ver);
                }
            } else if (isLocalAheadOfRemote(localMember, remoteDigest)) {
                // Local node has newer state than remote node -> Send updates to remote
                delta.addMemberUpdate(localMember);
                for (Map.Entry<String, String> state : localMember.getApplicationState().entrySet()) {
                    long ver = localMember.getStateVersions().getOrDefault(state.getKey(), 1L);
                    delta.addStateValue(state.getKey(), state.getValue(), ver);
                }
            }
        }

        // Check if remote node has newer state than local node
        for (GossipDigest remoteDigest : remoteDigests) {
            NodeMember localMember = localNode.getMember(remoteDigest.getNodeId());
            if (localMember == null || isRemoteAheadOfLocal(remoteDigest, localMember)) {
                // Remote node is ahead -> Request full state update from remote
                delta.addRequestedDigest(remoteDigest);
            }
        }

        Message ack = Message.digestAck(localNode.getNodeId(), synMessage.getSender(), delta);
        channel.send(ack);
    }

    private boolean isLocalAheadOfRemote(NodeMember local, GossipDigest remote) {
        if (local.getIncarnationNumber() > remote.getIncarnationNumber()) return true;
        if (local.getIncarnationNumber() == remote.getIncarnationNumber()) {
            if (local.getStatus() == NodeStatus.SUSPECT && remote.getStatus() == NodeStatus.ALIVE) return true;
            if (local.getStatus() == NodeStatus.DEAD && remote.getStatus() != NodeStatus.DEAD) return true;
        }
        if (local.getHeartbeatSequence() > remote.getHeartbeatSequence()) return true;
        return local.getMaxStateVersion() > remote.getMaxStateVersion();
    }

    private boolean isRemoteAheadOfLocal(GossipDigest remote, NodeMember local) {
        if (remote.getIncarnationNumber() > local.getIncarnationNumber()) return true;
        if (remote.getIncarnationNumber() == local.getIncarnationNumber()) {
            if (remote.getStatus() == NodeStatus.SUSPECT && local.getStatus() == NodeStatus.ALIVE) return true;
            if (remote.getStatus() == NodeStatus.DEAD && local.getStatus() != NodeStatus.DEAD) return true;
        }
        if (remote.getHeartbeatSequence() > local.getHeartbeatSequence()) return true;
        return remote.getMaxStateVersion() > local.getMaxStateVersion();
    }

    public void handleDigestAck(Message ackMessage, NetworkChannel channel) {
        GossipDelta deltaFromRemote = ackMessage.getDelta();

        // 1. Apply updates sent by remote node
        applyDelta(deltaFromRemote);

        // 2. Prepare ACK2 for any digests requested by remote node
        if (!deltaFromRemote.getRequestedDigests().isEmpty()) {
            GossipDelta deltaForRemote = new GossipDelta();
            for (GossipDigest req : deltaFromRemote.getRequestedDigests()) {
                NodeMember member = localNode.getMember(req.getNodeId());
                if (member != null) {
                    deltaForRemote.addMemberUpdate(member);
                    for (Map.Entry<String, String> state : member.getApplicationState().entrySet()) {
                        long ver = member.getStateVersions().getOrDefault(state.getKey(), 1L);
                        deltaForRemote.addStateValue(state.getKey(), state.getValue(), ver);
                    }
                }
            }
            Message ack2 = Message.digestAck2(localNode.getNodeId(), ackMessage.getSender(), deltaForRemote);
            channel.send(ack2);
        }
    }

    public void handleDigestAck2(Message ack2Message) {
        applyDelta(ack2Message.getDelta());
    }

    private void applyDelta(GossipDelta delta) {
        if (delta == null) return;

        // Apply member updates
        for (NodeMember update : delta.getMemberUpdates()) {
            localNode.mergeMemberState(update);
        }

        // Apply key-value updates
        Map<String, String> values = delta.getStateValues();
        Map<String, Long> versions = delta.getStateVersions();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            long ver = versions.getOrDefault(key, 1L);
            localNode.applyStateUpdate(key, val, ver);
        }
    }
}
