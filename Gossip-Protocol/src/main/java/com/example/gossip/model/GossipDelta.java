package com.example.gossip.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates concrete delta state updates exchanged between gossip nodes.
 */
public class GossipDelta {
    private final List<NodeMember> memberUpdates = new ArrayList<>();
    private final Map<String, String> stateValues = new HashMap<>();
    private final Map<String, Long> stateVersions = new HashMap<>();
    private final List<GossipDigest> requestedDigests = new ArrayList<>();

    public GossipDelta() {
    }

    public void addMemberUpdate(NodeMember member) {
        this.memberUpdates.add(member.copy());
    }

    public void addStateValue(String key, String value, long version) {
        this.stateValues.put(key, value);
        this.stateVersions.put(key, version);
    }

    public void addRequestedDigest(GossipDigest digest) {
        this.requestedDigests.add(digest);
    }

    public List<NodeMember> getMemberUpdates() {
        return memberUpdates;
    }

    public Map<String, String> getStateValues() {
        return stateValues;
    }

    public Map<String, Long> getStateVersions() {
        return stateVersions;
    }

    public List<GossipDigest> getRequestedDigests() {
        return requestedDigests;
    }

    public boolean isEmpty() {
        return memberUpdates.isEmpty() && stateValues.isEmpty() && requestedDigests.isEmpty();
    }
}
