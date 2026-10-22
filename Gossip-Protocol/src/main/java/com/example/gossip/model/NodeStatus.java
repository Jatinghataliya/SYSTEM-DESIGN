package com.example.gossip.model;

/**
 * Health and membership state of a node in the cluster.
 */
public enum NodeStatus {
    /**
     * Node is active and responding to probes.
     */
    ALIVE,

    /**
     * Node failed a direct probe; currently undergoing indirect probes (SWIM protocol).
     */
    SUSPECT,

    /**
     * Node failed suspicion period without refutation; declared dead.
     */
    DEAD,

    /**
     * Node intentionally left the cluster (graceful shutdown).
     */
    LEFT
}
