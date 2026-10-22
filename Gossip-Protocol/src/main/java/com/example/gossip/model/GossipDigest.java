package com.example.gossip.model;

import java.util.Objects;

/**
 * Digest entry summarizing a node's known version, status, and incarnation for anti-entropy.
 */
public final class GossipDigest {
    private final NodeId nodeId;
    private final NodeStatus status;
    private final long heartbeatSequence;
    private final long incarnationNumber;
    private final long maxStateVersion;

    public GossipDigest(NodeId nodeId, NodeStatus status, long heartbeatSequence, long incarnationNumber, long maxStateVersion) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.heartbeatSequence = heartbeatSequence;
        this.incarnationNumber = incarnationNumber;
        this.maxStateVersion = maxStateVersion;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public long getHeartbeatSequence() {
        return heartbeatSequence;
    }

    public long getIncarnationNumber() {
        return incarnationNumber;
    }

    public long getMaxStateVersion() {
        return maxStateVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GossipDigest that = (GossipDigest) o;
        return heartbeatSequence == that.heartbeatSequence &&
                incarnationNumber == that.incarnationNumber &&
                maxStateVersion == that.maxStateVersion &&
                status == that.status &&
                nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, status, heartbeatSequence, incarnationNumber, maxStateVersion);
    }

    @Override
    public String toString() {
        return "Digest(" + nodeId.getId() + ", " + status + ", inc=" + incarnationNumber + ", hb=" + heartbeatSequence + ", v=" + maxStateVersion + ")";
    }
}
