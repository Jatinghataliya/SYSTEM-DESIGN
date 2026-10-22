package com.example.gossip.model;

import java.util.Objects;

/**
 * Unique identifier for a node in the gossip network.
 */
public final class NodeId implements Comparable<NodeId> {
    private final String id;
    private final String host;
    private final int port;

    public NodeId(String id, String host, int port) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.port = port;
    }

    public static NodeId of(String id, int port) {
        return new NodeId(id, "127.0.0.1", port);
    }

    public String getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeId nodeId = (NodeId) o;
        return port == nodeId.port && id.equals(nodeId.id) && host.equals(nodeId.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, host, port);
    }

    @Override
    public int compareTo(NodeId o) {
        return this.id.compareTo(o.id);
    }

    @Override
    public String toString() {
        return id + "(" + host + ":" + port + ")";
    }
}
