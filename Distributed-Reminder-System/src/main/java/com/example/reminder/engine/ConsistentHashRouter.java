package com.example.reminder.engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Consistent hash ring routing reminders and cancellation commands to scheduler worker nodes.
 */
public class ConsistentHashRouter {

    private final int numberOfReplicas;
    private final SortedMap<Long, SchedulerNode> circle = new TreeMap<>();
    private final List<SchedulerNode> nodes = new ArrayList<>();

    public ConsistentHashRouter(int numberOfReplicas, Collection<SchedulerNode> initialNodes) {
        this.numberOfReplicas = numberOfReplicas;
        if (initialNodes != null) {
            for (SchedulerNode node : initialNodes) {
                addNode(node);
            }
        }
    }

    public synchronized void addNode(SchedulerNode node) {
        nodes.add(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = hash(node.getNodeId() + "-vnode-" + i);
            circle.put(hash, node);
        }
    }

    public synchronized void removeNode(SchedulerNode node) {
        nodes.remove(node);
        for (int i = 0; i < numberOfReplicas; i++) {
            long hash = hash(node.getNodeId() + "-vnode-" + i);
            circle.remove(hash);
        }
    }

    public synchronized SchedulerNode getNode(String key) {
        if (circle.isEmpty()) {
            return null;
        }
        long hash = hash(key);
        if (!circle.containsKey(hash)) {
            SortedMap<Long, SchedulerNode> tailMap = circle.tailMap(hash);
            hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
        }
        return circle.get(hash);
    }

    public List<SchedulerNode> getAllNodes() {
        return Collections.unmodifiableList(nodes);
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((long) (digest[3] & 0xFF) << 24) |
                    ((long) (digest[2] & 0xFF) << 16) |
                    ((long) (digest[1] & 0xFF) << 8) |
                    ((long) (digest[0] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            return key.hashCode() & 0xffffffffL;
        }
    }
}
