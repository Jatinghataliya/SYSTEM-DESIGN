package com.example.consistenthashing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Measurements used by the demo and tests: how many keys move, and how evenly they spread. */
public final class Analysis {

    private Analysis() {
    }

    public static List<String> keys(int count) {
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add("user:" + i);
        }
        return keys;
    }

    public static Map<String, String> assignments(NodeLocator locator, List<String> keys) {
        Map<String, String> map = new HashMap<>(keys.size() * 2);
        for (String key : keys) {
            map.put(key, locator.locate(key));
        }
        return map;
    }

    /** Percentage of keys whose owner differs between two snapshots. */
    public static double movedPercent(Map<String, String> before, Map<String, String> after) {
        int moved = 0;
        for (Map.Entry<String, String> e : before.entrySet()) {
            if (!e.getValue().equals(after.get(e.getKey()))) {
                moved++;
            }
        }
        return 100.0 * moved / before.size();
    }

    public static Map<String, Integer> countPerNode(Map<String, String> assignments) {
        Map<String, Integer> counts = new HashMap<>();
        for (String node : assignments.values()) {
            counts.merge(node, 1, Integer::sum);
        }
        return counts;
    }

    /** Max node load divided by the average load. 1.00 is perfect; 1.30 means the busiest node has 30% extra. */
    public static double peakToAverage(Map<String, Integer> counts, int totalNodes, int totalKeys) {
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return max / ((double) totalKeys / totalNodes);
    }

    /** Keys that stayed put, as moved by each side. Used to check "only the removed node's keys move". */
    public static int movedNotFrom(Map<String, String> before, Map<String, String> after, String removedNode) {
        int count = 0;
        for (Map.Entry<String, String> e : before.entrySet()) {
            boolean moved = !e.getValue().equals(after.get(e.getKey()));
            if (moved && !e.getValue().equals(removedNode)) {
                count++;
            }
        }
        return count;
    }
}
