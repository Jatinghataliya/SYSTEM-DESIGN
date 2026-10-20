package com.example.consistenthashing;

import java.util.List;

/** Maps a key to the node that owns it. Every strategy in this project implements this. */
public interface NodeLocator {

    /** Short label for tables and logs. */
    String name();

    void addNode(String node);

    /** @throws UnsupportedOperationException if the strategy cannot remove that node */
    void removeNode(String node);

    /** @throws IllegalStateException if there are no nodes */
    String locate(String key);

    /** Nodes in the order they were added. */
    List<String> nodes();
}
