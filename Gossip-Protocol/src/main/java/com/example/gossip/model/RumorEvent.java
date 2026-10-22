package com.example.gossip.model;

import java.util.Objects;

/**
 * Event broadcast through rumor mongering across the cluster.
 */
public final class RumorEvent {
    private final String eventId;
    private final NodeId originNode;
    private final String key;
    private final String value;
    private final long version;
    private final long timestamp;

    public RumorEvent(String eventId, NodeId originNode, String key, String value, long version, long timestamp) {
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.originNode = Objects.requireNonNull(originNode, "originNode cannot be null");
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.value = value;
        this.version = version;
        this.timestamp = timestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public NodeId getOriginNode() {
        return originNode;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public long getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RumorEvent that = (RumorEvent) o;
        return version == that.version && eventId.equals(that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, version);
    }

    @Override
    public String toString() {
        return "Rumor{" + eventId + ": " + key + "=" + value + " (v" + version + ") by " + originNode.getId() + "}";
    }
}
