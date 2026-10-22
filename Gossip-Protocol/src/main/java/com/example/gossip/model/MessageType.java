package com.example.gossip.model;

/**
 * Message types used in Gossip communication.
 */
public enum MessageType {
    // SWIM Failure Detector Probes
    PING,
    ACK,
    PING_REQ,

    // Epidemic Dissemination / Rumor Mongering
    RUMOR_EVENT,

    // Anti-Entropy (3-Phase State Reconciliation)
    DIGEST_SYN,
    DIGEST_ACK,
    DIGEST_ACK2
}
