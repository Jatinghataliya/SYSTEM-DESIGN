package com.bloomfilter;

public class BloomFilterExample {

    public static void main(String[] args) {

        // -------------------------------------------------------
        // EXAMPLE 1: Banned Username Checker
        // -------------------------------------------------------
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║        BLOOM FILTER — Banned Username Demo       ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        BloomFilter bf = new BloomFilter(500, 3);

        String[] bannedUsers = {"admin", "root", "superuser", "hacker99", "spambot"};

        System.out.println("► Adding banned usernames...");
        for (String user : bannedUsers) {
            bf.add(user);
            System.out.println("  [+] Added: '" + user + "'");
        }
        System.out.println("\n" + bf + "\n");

        System.out.println("► Checking usernames at registration:");
        System.out.println("  " + "-".repeat(50));

        String[] testUsers = {
            "alice",       // clean
            "bob",         // clean
            "admin",       // banned
            "hacker99",    // banned
            "charlie",     // clean
            "root",        // banned
            "dave123",     // clean
            "spambot",     // banned
            "eve"          // clean
        };

        int blocked = 0, allowed = 0;
        for (String user : testUsers) {
            boolean found = bf.contains(user);
            String status = found
                ? "⛔  POSSIBLY BANNED  → block or verify"
                : "✅  Definitely clean → allow";
            System.out.printf("  %-12s : %s%n", "'" + user + "'", status);
            if (found) blocked++; else allowed++;
        }

        System.out.println("  " + "-".repeat(50));
        System.out.printf("  Blocked: %d  |  Allowed: %d%n", blocked, allowed);
        System.out.printf("  Estimated False Positive Rate: %.6f%%%n%n",
            bf.falsePositiveRate() * 100);

        // -------------------------------------------------------
        // EXAMPLE 2: Optimal Configuration Calculator
        // -------------------------------------------------------
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     BLOOM FILTER — Optimal Config Calculator     ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        System.out.println("► False positive rate vs. bit array size (for 1000 elements, k=3):\n");
        System.out.printf("  %-12s %-15s %-15s%n", "Size (bits)", "FP Rate (%)", "Bits/Element");
        System.out.println("  " + "-".repeat(44));

        int[] sizes = {1000, 2000, 5000, 10000, 50000, 100000};
        for (int size : sizes) {
            BloomFilter temp = new BloomFilter(size, 3);
            for (int i = 0; i < 1000; i++) temp.add("user" + i);
            System.out.printf("  %-12d %-15.6f %-15.1f%n",
                size, temp.falsePositiveRate() * 100, (double) size / 1000);
        }

        // -------------------------------------------------------
        // EXAMPLE 3: Scale Test
        // -------------------------------------------------------
        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║         BLOOM FILTER — Scale Test (10k items)    ║");
        System.out.println("╚══════════════════════════════════════════════════╝\n");

        BloomFilter largeBf = new BloomFilter(100000, 5);
        System.out.println("► Inserting 10,000 email addresses...");
        for (int i = 0; i < 10000; i++) {
            largeBf.add("user" + i + "@example.com");
        }
        System.out.println("  " + largeBf);

        System.out.println("\n► Checking 5 known members and 5 non-members:");
        String[] knownMembers    = {"user0@example.com", "user500@example.com", "user9999@example.com", "user1234@example.com", "user7777@example.com"};
        String[] nonMembers      = {"ghost@example.com", "nobody@test.com", "fake@domain.com", "test@unknown.com", "missing@here.com"};

        System.out.println("  Known members (should all be TRUE):");
        for (String email : knownMembers) {
            System.out.printf("    %-35s → %s%n", email, largeBf.contains(email) ? "✅ Found" : "❌ MISSED (false negative — impossible!)");
        }
        System.out.println("  Non-members (should be FALSE, but may have false positives):");
        for (String email : nonMembers) {
            System.out.printf("    %-35s → %s%n", email, largeBf.contains(email) ? "⚠️  False Positive!" : "✅ Correctly absent");
        }

        System.out.printf("%n  Final FP Rate: %.6f%%%n", largeBf.falsePositiveRate() * 100);
        System.out.println("\n✔ Done.");
    }
}
