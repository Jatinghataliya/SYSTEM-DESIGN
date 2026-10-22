package com.example.rsync;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Educational demonstration of Rsync rolling checksum algorithm and delta transfer savings.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("               RSYNC ALGORITHM - REMOTE FILE SYNCHRONIZATION DEMO               ");
        System.out.println("================================================================================");

        int blockSize = 64; // Small block size for compact readable demonstration
        Rsync rsync = new Rsync(blockSize);

        // Scenario 1: Insertion at the very beginning (Demonstrates rolling checksum offset invariance)
        runScenario(
                "Scenario 1: Insertion at Start (Shift Offset Test)",
                "System Design is the process of defining the architecture, components, modules, interfaces, and data for a system to satisfy specified requirements. Systems design could be seen as the application of systems theory to product development. There is some overlap with the disciplines of systems analysis, systems architecture and systems engineering.",
                "HEADER: [v2.0 UPDATE] -> System Design is the process of defining the architecture, components, modules, interfaces, and data for a system to satisfy specified requirements. Systems design could be seen as the application of systems theory to product development. There is some overlap with the disciplines of systems analysis, systems architecture and systems engineering.",
                rsync
        );

        // Scenario 2: Small in-place modification in large text
        runScenario(
                "Scenario 2: Single Word In-Place Edit in Large Document",
                "Distributed systems require robust algorithms like Paxos, Raft, Consistent Hashing, and Merkle Trees to achieve high availability and fault tolerance across nodes.",
                "Distributed systems require robust algorithms like Paxos, Raft, RSYNC DELTA SYNC, and Merkle Trees to achieve high availability and fault tolerance across nodes.",
                rsync
        );

        // Scenario 3: Append-only log file
        runScenario(
                "Scenario 3: Append Log Entries (Telemetry / Audit Logs)",
                "[2025-01-01 10:00:00] INFO Server started on port 8080\n[2025-01-01 10:00:01] INFO Database connected\n[2025-01-01 10:00:02] INFO Cache initialized\n",
                "[2025-01-01 10:00:00] INFO Server started on port 8080\n[2025-01-01 10:00:01] INFO Database connected\n[2025-01-01 10:00:02] INFO Cache initialized\n[2025-01-01 10:00:03] INFO New client connected: 192.168.1.50\n[2025-01-01 10:00:04] WARN High memory usage detected\n",
                rsync
        );

        // Scenario 4: Identical files
        runScenario(
                "Scenario 4: Identical Files (Zero Literal Byte Transfer)",
                "This document is identical on both client and server nodes. No modifications have been made.",
                "This document is identical on both client and server nodes. No modifications have been made.",
                rsync
        );

        System.out.println("================================================================================");
        System.out.println("All demonstrations completed successfully!");
        System.out.println("================================================================================");
    }

    private static void runScenario(String title, String basisText, String sourceText, Rsync rsync) {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(">>> " + title);
        System.out.println("--------------------------------------------------------------------------------");

        byte[] basisBytes = basisText.getBytes(StandardCharsets.UTF_8);
        byte[] sourceBytes = sourceText.getBytes(StandardCharsets.UTF_8);

        RsyncSyncResult result = rsync.sync(basisBytes, sourceBytes);
        RsyncStats stats = result.stats();

        boolean match = Arrays.equals(sourceBytes, result.reconstructedData());

        System.out.println("Basis file size        : " + stats.basisSize() + " bytes (divided into " + stats.totalBlocksInBasis() + " blocks of " + rsync.getBlockSize() + "B)");
        System.out.println("Source file size       : " + stats.originalSourceSize() + " bytes");
        System.out.println("Delta operations       : " + result.deltaOps().size() + " ops");

        for (int i = 0; i < result.deltaOps().size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + result.deltaOps().get(i));
        }

        System.out.println("Matched basis blocks   : " + stats.matchedBlocksCount() + " (" + stats.matchedBytes() + " bytes reused)");
        System.out.println("Literal data sent      : " + stats.literalChunksCount() + " chunks (" + stats.literalBytesTransferred() + " bytes)");
        System.out.println("Signature overhead     : " + stats.signatureOverheadBytes() + " bytes");
        System.out.println("Total network transfer : " + stats.totalNetworkTransferredBytes() + " bytes");
        System.out.printf("Network bandwidth saved: %.2f%%\n", stats.bandwidthSavingsPercentage());
        System.out.println("Reconstruction match   : " + (match ? "SUCCESS (Bit-for-bit identical)" : "FAILED"));
    }
}
