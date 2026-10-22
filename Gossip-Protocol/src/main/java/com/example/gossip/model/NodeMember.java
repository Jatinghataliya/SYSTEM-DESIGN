package com.example.gossip.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * State and metadata held about a cluster member.
 */
public class NodeMember {
    private final NodeId nodeId;
    private NodeStatus status;
    private long heartbeatSequence;
    private long incarnationNumber;
    private long lastUpdatedTick;
    private long suspicionStartTick;
    private final Map<String, String> applicationState;
    private final Map<String, Long> stateVersions;

    public NodeMember(NodeId nodeId, NodeStatus status, long heartbeatSequence, long incarnationNumber, long lastUpdatedTick) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.heartbeatSequence = heartbeatSequence;
        this.incarnationNumber = incarnationNumber;
        this.lastUpdatedTick = lastUpdatedTick;
        this.suspicionStartTick = -1;
        this.applicationState = new HashMap<>();
        this.stateVersions = new HashMap<>();
    }

    public synchronized NodeMember copy() {
        NodeMember copy = new NodeMember(nodeId, status, heartbeatSequence, incarnationNumber, lastUpdatedTick);
        copy.suspicionStartTick = this.suspicionStartTick;
        copy.applicationState.putAll(this.applicationState);
        copy.stateVersions.putAll(this.stateVersions);
        return copy;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public synchronized NodeStatus getStatus() {
        return status;
    }

    public synchronized void setStatus(NodeStatus status) {
        this.status = status;
    }

    public synchronized long getHeartbeatSequence() {
        return heartbeatSequence;
    }

    public synchronized void setHeartbeatSequence(long heartbeatSequence) {
        this.heartbeatSequence = heartbeatSequence;
    }

    public synchronized long getIncarnationNumber() {
        return incarnationNumber;
    }

    public synchronized void setIncarnationNumber(long incarnationNumber) {
        this.incarnationNumber = incarnationNumber;
    }

    public synchronized long getLastUpdatedTick() {
        return lastUpdatedTick;
    }

    public synchronized void setLastUpdatedTick(long lastUpdatedTick) {
        this.lastUpdatedTick = lastUpdatedTick;
    }

    public synchronized long getSuspicionStartTick() {
        return suspicionStartTick;
    }

    public synchronized void setSuspicionStartTick(long suspicionStartTick) {
        this.suspicionStartTick = suspicionStartTick;
    }

    public synchronized Map<String, String> getApplicationState() {
        return Collections.unmodifiableMap(new HashMap<>(applicationState));
    }

    public synchronized Map<String, Long> getStateVersions() {
        return Collections.unmodifiableMap(new HashMap<>(stateVersions));
    }

    public synchronized void putState(String key, String value, long version) {
        Long currentVersion = stateVersions.get(key);
        if (currentVersion == null || version > currentVersion) {
            applicationState.put(key, value);
            stateVersions.put(key, version);
        }
    }

    public synchronized long getMaxStateVersion() {
        return stateVersions.values().stream().mapToLong(Long::longValue).max().orElse(0L);
    }

    @Override
    public synchronized String toString() {
        return "Member{" +
                "id=" + nodeId.getId() +
                ", status=" + status +
                ", hb=" + heartbeatSequence +
                ", inc=" + incarnationNumber +
                ", stateCount=" + applicationState.size() +
                '}';
    }
}
