package com.example.gossip.core;

import com.example.gossip.model.*;
import com.example.gossip.network.NetworkChannel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SWIM-style Failure Detector with Suspicion Mechanism and Refutation.
 *
 * Paper: "SWIM: Weakly-Consistent Infection-Style Process Group Membership Protocol"
 * (Das, Gupta, Motivala - IEEE DSN 2002)
 */
public class FailureDetector {
    private final GossipNode localNode;
    private final int indirectProbeCount; // k parameter (e.g. 3)
    private final long directAckTimeoutTicks;
    private final long indirectAckTimeoutTicks;
    private final long suspicionTimeoutTicks;

    private final Map<Long, ProbeSession> pendingProbes = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public static class ProbeSession {
        final long sequenceNumber;
        final NodeId targetNode;
        final long initiatedTick;
        final boolean isIndirect;
        final Set<NodeId> helpers;

        ProbeSession(long sequenceNumber, NodeId targetNode, long initiatedTick, boolean isIndirect, Set<NodeId> helpers) {
            this.sequenceNumber = sequenceNumber;
            this.targetNode = targetNode;
            this.initiatedTick = initiatedTick;
            this.isIndirect = isIndirect;
            this.helpers = helpers;
        }
    }

    public FailureDetector(GossipNode localNode, int indirectProbeCount,
                           long directAckTimeoutTicks, long indirectAckTimeoutTicks,
                           long suspicionTimeoutTicks) {
        this.localNode = localNode;
        this.indirectProbeCount = indirectProbeCount;
        this.directAckTimeoutTicks = directAckTimeoutTicks;
        this.indirectAckTimeoutTicks = indirectAckTimeoutTicks;
        this.suspicionTimeoutTicks = suspicionTimeoutTicks;
    }

    /**
     * Called on each protocol tick to drive failure detection.
     */
    public void onTick(long currentTick, NetworkChannel channel) {
        checkProbeTimeouts(currentTick, channel);
        checkSuspicionTimeouts(currentTick);

        // Periodically initiate probe to a random member
        initiateProbe(currentTick, channel);
    }

    private void initiateProbe(long currentTick, NetworkChannel channel) {
        List<NodeMember> candidates = localNode.getProbingCandidates();
        if (candidates.isEmpty()) {
            return;
        }

        NodeMember target = candidates.get(random.nextInt(candidates.size()));
        long seq = localNode.nextSequence();

        pendingProbes.put(seq, new ProbeSession(seq, target.getNodeId(), currentTick, false, Collections.emptySet()));

        Message ping = Message.ping(localNode.getNodeId(), target.getNodeId(), seq, localNode.getLocalMemberState());
        channel.send(ping);
    }

    private void checkProbeTimeouts(long currentTick, NetworkChannel channel) {
        Iterator<Map.Entry<Long, ProbeSession>> it = pendingProbes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ProbeSession> entry = it.next();
            ProbeSession probe = entry.getValue();

            if (!probe.isIndirect) {
                // Direct probe phase timeout check
                if (currentTick - probe.initiatedTick >= directAckTimeoutTicks) {
                    it.remove();
                    // Escalate to Indirect Probing via k peers (SWIM PING-REQ)
                    escalateToIndirectProbe(probe.targetNode, currentTick, channel);
                }
            } else {
                // Indirect probe phase timeout check
                if (currentTick - probe.initiatedTick >= indirectAckTimeoutTicks) {
                    it.remove();
                    // Target failed indirect probes -> Mark SUSPECT
                    localNode.markSuspect(probe.targetNode, currentTick);
                }
            }
        }
    }

    private void escalateToIndirectProbe(NodeId target, long currentTick, NetworkChannel channel) {
        List<NodeMember> potentialHelpers = localNode.getHealthyPeersExcluding(target);
        if (potentialHelpers.isEmpty()) {
            // No helper peers available, directly mark suspect
            localNode.markSuspect(target, currentTick);
            return;
        }

        Collections.shuffle(potentialHelpers, random);
        int helpersToPick = Math.min(indirectProbeCount, potentialHelpers.size());
        Set<NodeId> helpers = new HashSet<>();

        long indirectSeq = localNode.nextSequence();
        for (int i = 0; i < helpersToPick; i++) {
            NodeId helperId = potentialHelpers.get(i).getNodeId();
            helpers.add(helperId);
            Message pingReq = Message.pingReq(localNode.getNodeId(), helperId, target, indirectSeq);
            channel.send(pingReq);
        }

        pendingProbes.put(indirectSeq, new ProbeSession(indirectSeq, target, currentTick, true, helpers));
    }

    private void checkSuspicionTimeouts(long currentTick) {
        for (NodeMember member : localNode.getAllMembers()) {
            if (member.getStatus() == NodeStatus.SUSPECT) {
                long suspectAge = currentTick - member.getSuspicionStartTick();
                if (member.getSuspicionStartTick() > 0 && suspectAge >= suspicionTimeoutTicks) {
                    localNode.markDead(member.getNodeId(), currentTick);
                }
            }
        }
    }

    public void handleAck(Message ackMessage) {
        // Direct or indirect ACK received, clear matching pending probe
        pendingProbes.remove(ackMessage.getSequenceNumber());
        localNode.markAlive(ackMessage.getSender(), ackMessage.getMemberState());
    }

    public void handlePingReq(Message pingReq, NetworkChannel channel) {
        NodeId originalRequester = pingReq.getSender();
        NodeId targetToProbe = pingReq.getTargetProbeNode();
        long originalSeq = pingReq.getSequenceNumber();

        // Forward a ping to target with request context
        long intermediateSeq = localNode.nextSequence();
        pendingProbes.put(intermediateSeq, new ProbeSession(intermediateSeq, targetToProbe, channel.getCurrentTick(), false, Collections.singleton(originalRequester)));

        Message ping = Message.ping(localNode.getNodeId(), targetToProbe, intermediateSeq, localNode.getLocalMemberState());
        channel.send(ping);
    }
}
