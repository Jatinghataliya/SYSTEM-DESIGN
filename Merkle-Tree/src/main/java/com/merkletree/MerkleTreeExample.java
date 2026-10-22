package com.merkletree;

import java.util.Arrays;
import java.util.List;

/**
 * Three live demonstrations of the Merkle Tree:
 *
 * <ol>
 *   <li>Blockchain transaction batch — build a Merkle root over 6 transactions.</li>
 *   <li>Tamper detection — show how a single changed block invalidates the root.</li>
 *   <li>Inclusion proof (audit proof) — prove one leaf belongs without revealing others.</li>
 * </ol>
 */
public class MerkleTreeExample {

    public static void main(String[] args) {
        demo1_BlockchainTransactions();
        demo2_TamperDetection();
        demo3_InclusionProof();
    }

    // =========================================================================
    // Demo 1 — Blockchain transaction batch
    // =========================================================================
    private static void demo1_BlockchainTransactions() {
        System.out.println("=".repeat(60));
        System.out.println("DEMO 1 — Blockchain Transaction Batch");
        System.out.println("=".repeat(60));

        List<String> transactions = Arrays.asList(
                "Alice -> Bob : 1.5 BTC",
                "Bob -> Charlie : 0.8 BTC",
                "Charlie -> Dave : 2.0 BTC",
                "Dave -> Eve : 0.3 BTC",
                "Eve -> Alice : 1.1 BTC",
                "Frank -> Grace : 0.5 BTC"
        );

        MerkleTree tree = new MerkleTree();
        tree.build(transactions);

        System.out.println("\nTransactions in block:");
        for (int i = 0; i < transactions.size(); i++) {
            System.out.printf("  [%d] %s%n", i, transactions.get(i));
        }

        System.out.println("\nMerkle Root: " + tree.getRootHash());
        tree.printTree();
    }

    // =========================================================================
    // Demo 2 — Tamper detection
    // =========================================================================
    private static void demo2_TamperDetection() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 2 — Tamper Detection");
        System.out.println("=".repeat(60));

        List<String> original = Arrays.asList(
                "Block A: user_id=1, amount=100",
                "Block B: user_id=2, amount=200",
                "Block C: user_id=3, amount=300",
                "Block D: user_id=4, amount=400"
        );

        MerkleTree originalTree = new MerkleTree();
        originalTree.build(original);
        String originalRoot = originalTree.getRootHash();

        System.out.println("\nOriginal Merkle Root : " + originalRoot);

        // Attacker tampers with Block C
        List<String> tampered = Arrays.asList(
                "Block A: user_id=1, amount=100",
                "Block B: user_id=2, amount=200",
                "Block C: user_id=3, amount=9999",   // ← tampered!
                "Block D: user_id=4, amount=400"
        );

        MerkleTree tamperedTree = new MerkleTree();
        tamperedTree.build(tampered);
        String tamperedRoot = tamperedTree.getRootHash();

        System.out.println("Tampered Merkle Root : " + tamperedRoot);

        if (!originalRoot.equals(tamperedRoot)) {
            System.out.println("\n⚠️  TAMPER DETECTED — roots do NOT match!");
        } else {
            System.out.println("\n✅ Roots match — no tampering detected.");
        }
    }

    // =========================================================================
    // Demo 3 — Inclusion proof (audit proof)
    // =========================================================================
    private static void demo3_InclusionProof() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 3 — Inclusion Proof (Audit Proof)");
        System.out.println("=".repeat(60));

        List<String> files = Arrays.asList(
                "document_contract.pdf",
                "invoice_2024_01.pdf",
                "invoice_2024_02.pdf",
                "audit_report.pdf",
                "shareholder_agreement.pdf",
                "terms_of_service.pdf",
                "privacy_policy.pdf",
                "nda_template.pdf"
        );

        MerkleTree tree = new MerkleTree();
        tree.build(files);

        String rootHash = tree.getRootHash();
        System.out.println("\nMerkle Root: " + rootHash);

        // Prove that "invoice_2024_02.pdf" (index 2) is in the tree
        int targetIndex = 2;
        String targetData = files.get(targetIndex);
        List<MerkleTree.ProofStep> proof = tree.getProof(targetIndex);

        System.out.println("\nProving inclusion of: \"" + targetData + "\" (index " + targetIndex + ")");
        System.out.println("Proof steps (" + proof.size() + " hashes needed):");
        for (int i = 0; i < proof.size(); i++) {
            System.out.printf("  Step %d: %s%n", i + 1, proof.get(i));
        }

        boolean valid = MerkleTree.verify(targetData, proof, rootHash);
        System.out.println("\nProof verification: " + (valid ? "✅ VALID — leaf is in the tree" : "❌ INVALID"));

        // Attempt verification with tampered data
        String tampered = "invoice_2024_02_FAKE.pdf";
        boolean invalidResult = MerkleTree.verify(tampered, proof, rootHash);
        System.out.println("Tampered data verification: " + (invalidResult ? "✅ VALID" : "❌ REJECTED — tampered data detected"));
    }
}
