package com.example.gossip.core;

import com.example.gossip.model.Message;
import com.example.gossip.model.NodeId;
import com.example.gossip.model.NodeMember;
import com.example.gossip.model.RumorEvent;
import com.example.gossip.network.NetworkChannel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Epidemic Dissemination / Rumor Mongering Engine.
 * Implements SIR (Susceptible -> Infectious -> Removed) epidemic model for rapid O(log N) broadcasting.
 */
public class RumorMongerEngine {
    private final GossipNode localNode;
    private final int fanout; // Number of peers to gossip to per round (e.g. 2 or 3)
    private final int maxInfectionRounds; // Threshold after which rumor transitions to Removed

    private static class RumorRecord {
        final RumorEvent event;
        int transmissionCount = 0;

        RumorRecord(RumorEvent event) {
            this.event = event;
        }
    }

    private final Map<String, RumorRecord> activeRumors = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public RumorMongerEngine(GossipNode localNode, int fanout, int maxInfectionRounds) {
        this.localNode = localNode;
        this.fanout = fanout;
        this.maxInfectionRounds = maxInfectionRounds;
    }

    public void broadcastNewRumor(String key, String value, long version, long currentTick) {
        String eventId = UUID.randomUUID().toString().substring(0, 8);
        RumorEvent rumor = new RumorEvent(eventId, localNode.getNodeId(), key, value, version, currentTick);
        activeRumors.put(rumor.getEventId(), new RumorRecord(rumor));
    }

    public boolean receiveRumor(RumorEvent rumor, long currentTick) {
        // Apply to local state first
        boolean stateUpdated = localNode.applyStateUpdate(rumor.getKey(), rumor.getValue(), rumor.getVersion());

        // Add to active rumors if newly received to spread further
        if (!activeRumors.containsKey(rumor.getEventId())) {
            activeRumors.put(rumor.getEventId(), new RumorRecord(rumor));
        }
        return stateUpdated;
    }

    public void onTick(long currentTick, NetworkChannel channel) {
        if (activeRumors.isEmpty()) {
            return;
        }

        List<NodeMember> peers = localNode.getHealthyPeersExcluding(localNode.getNodeId());
        if (peers.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<String, RumorRecord>> it = activeRumors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RumorRecord> entry = it.next();
            RumorRecord record = entry.getValue();

            Collections.shuffle(peers, random);
            int targetsToPick = Math.min(fanout, peers.size());

            for (int i = 0; i < targetsToPick; i++) {
                NodeId peer = peers.get(i).getNodeId();
                Message rumorMsg = Message.rumor(localNode.getNodeId(), peer, record.event);
                channel.send(rumorMsg);
            }

            record.transmissionCount++;
            if (record.transmissionCount >= maxInfectionRounds) {
                it.remove(); // Retired / Removed from infection pool
            }
        }
    }

    public int getActiveRumorCount() {
        return activeRumors.size();
    }
}
