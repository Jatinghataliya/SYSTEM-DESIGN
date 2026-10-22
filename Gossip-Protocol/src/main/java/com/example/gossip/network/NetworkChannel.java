package com.example.gossip.network;

import com.example.gossip.model.Message;
import com.example.gossip.model.NodeId;

/**
 * Channel interface through which nodes send messages.
 */
public interface NetworkChannel {
    /**
     * Send a message to the target recipient.
     */
    void send(Message message);

    /**
     * Get the current simulated clock tick.
     */
    long getCurrentTick();
}
