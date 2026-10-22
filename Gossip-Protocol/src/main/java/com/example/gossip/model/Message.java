package com.example.gossip.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Protocol message passed between gossip nodes over the network channel.
 */
public class Message {
    private final String messageId;
    private final NodeId sender;
    private final NodeId receiver;
    private final MessageType type;
    private final NodeId targetProbeNode; // Used for PING_REQ
    private final long sequenceNumber;
    private final List<GossipDigest> digests;
    private final GossipDelta delta;
    private final RumorEvent rumor;
    private final NodeMember memberState; // Piggybacked or specific member update

    private Message(Builder builder) {
        this.messageId = builder.messageId;
        this.sender = builder.sender;
        this.receiver = builder.receiver;
        this.type = builder.type;
        this.targetProbeNode = builder.targetProbeNode;
        this.sequenceNumber = builder.sequenceNumber;
        this.digests = builder.digests == null ? Collections.emptyList() : Collections.unmodifiableList(builder.digests);
        this.delta = builder.delta;
        this.rumor = builder.rumor;
        this.memberState = builder.memberState;
    }

    public static Message ping(NodeId sender, NodeId receiver, long seq, NodeMember localState) {
        return new Builder(sender, receiver, MessageType.PING)
                .sequenceNumber(seq)
                .memberState(localState)
                .build();
    }

    public static Message ack(NodeId sender, NodeId receiver, long seq, NodeMember localState) {
        return new Builder(sender, receiver, MessageType.ACK)
                .sequenceNumber(seq)
                .memberState(localState)
                .build();
    }

    public static Message pingReq(NodeId sender, NodeId intermediate, NodeId targetNode, long seq) {
        return new Builder(sender, intermediate, MessageType.PING_REQ)
                .targetProbeNode(targetNode)
                .sequenceNumber(seq)
                .build();
    }

    public static Message digestSyn(NodeId sender, NodeId receiver, List<GossipDigest> digests) {
        return new Builder(sender, receiver, MessageType.DIGEST_SYN)
                .digests(digests)
                .build();
    }

    public static Message digestAck(NodeId sender, NodeId receiver, GossipDelta delta) {
        return new Builder(sender, receiver, MessageType.DIGEST_ACK)
                .delta(delta)
                .build();
    }

    public static Message digestAck2(NodeId sender, NodeId receiver, GossipDelta delta) {
        return new Builder(sender, receiver, MessageType.DIGEST_ACK2)
                .delta(delta)
                .build();
    }

    public static Message rumor(NodeId sender, NodeId receiver, RumorEvent rumor) {
        return new Builder(sender, receiver, MessageType.RUMOR_EVENT)
                .rumor(rumor)
                .build();
    }

    public String getMessageId() {
        return messageId;
    }

    public NodeId getSender() {
        return sender;
    }

    public NodeId getReceiver() {
        return receiver;
    }

    public MessageType getType() {
        return type;
    }

    public NodeId getTargetProbeNode() {
        return targetProbeNode;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public List<GossipDigest> getDigests() {
        return digests;
    }

    public GossipDelta getDelta() {
        return delta;
    }

    public RumorEvent getRumor() {
        return rumor;
    }

    public NodeMember getMemberState() {
        return memberState;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", " + sender.getId() + " -> " + receiver.getId() +
                (targetProbeNode != null ? ", target=" + targetProbeNode.getId() : "") +
                (sequenceNumber > 0 ? ", seq=" + sequenceNumber : "") +
                '}';
    }

    public static class Builder {
        private String messageId = java.util.UUID.randomUUID().toString().substring(0, 8);
        private final NodeId sender;
        private final NodeId receiver;
        private final MessageType type;
        private NodeId targetProbeNode;
        private long sequenceNumber;
        private List<GossipDigest> digests;
        private GossipDelta delta;
        private RumorEvent rumor;
        private NodeMember memberState;

        public Builder(NodeId sender, NodeId receiver, MessageType type) {
            this.sender = Objects.requireNonNull(sender, "sender cannot be null");
            this.receiver = Objects.requireNonNull(receiver, "receiver cannot be null");
            this.type = Objects.requireNonNull(type, "type cannot be null");
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder targetProbeNode(NodeId target) {
            this.targetProbeNode = target;
            return this;
        }

        public Builder sequenceNumber(long seq) {
            this.sequenceNumber = seq;
            return this;
        }

        public Builder digests(List<GossipDigest> digests) {
            this.digests = digests;
            return this;
        }

        public Builder delta(GossipDelta delta) {
            this.delta = delta;
            return this;
        }

        public Builder rumor(RumorEvent rumor) {
            this.rumor = rumor;
            return this;
        }

        public Builder memberState(NodeMember memberState) {
            this.memberState = memberState;
            return this;
        }

        public Message build() {
            return new Message(this);
        }
    }
}
