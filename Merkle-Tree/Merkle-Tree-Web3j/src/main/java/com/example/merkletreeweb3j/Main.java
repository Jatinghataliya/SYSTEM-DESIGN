package com.example.merkletreeweb3j;

import java.util.Arrays;
import java.util.List;

/**
 * Three live demos of the Web3j Ethereum Merkle Tree:
 *
 * 1. Smart contract allowlist — build a Merkle root for whitelisted addresses.
 * 2. Tamper detection — show root changes when data is modified.
 * 3. Inclusion proof — prove one address is allowlisted without revealing others.
 *
 * Run: mvn compile exec:java
 */
public class Main {

    public static void main(String[] args) {
        demo1_AllowlistRoot();
        demo2_TamperDetection();
        demo3_InclusionProof();
    }

    // =========================================================================
    // Demo 1 — Smart contract NFT allowlist
    // =========================================================================
    private static void demo1_AllowlistRoot() {
        System.out.println("=".repeat(60));
        System.out.println("DEMO 1 — NFT Allowlist Merkle Root (Web3j / Keccak-256)");
        System.out.println("=".repeat(60));

        List<String> allowlist = Arrays.asList(
                "0xAb8483F64d9C6d1EcF9b849Ae677dD3315835cb2",
                "0x5B38Da6a701c568545dCfcB03FcB875f56beddC4",
                "0x4B20993Bc481177ec7E8f571ceCaE8A9e22C02db",
                "0x78731D3Ca6b7E34aC0F824c42a7cC18A495cabaB",
                "0x617F2E2fD72FD9D5503197092AC168c91465E7f2",
                "0x17F6AD8Ef982297579C203069C1DbfFE4348c208"
        );

        EthereumMerkleTree tree = new EthereumMerkleTree();
        tree.build(allowlist);

        System.out.println("\nAllowlisted addresses:");
        for (int i = 0; i < allowlist.size(); i++) {
            System.out.printf("  [%d] %s%n", i, allowlist.get(i));
        }
        System.out.println("\nMerkle Root (deploy to smart contract):");
        System.out.println("  " + tree.getRootHex());
    }

    // =========================================================================
    // Demo 2 — Tamper detection
    // =========================================================================
    private static void demo2_TamperDetection() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 2 — Tamper Detection");
        System.out.println("=".repeat(60));

        List<String> original = Arrays.asList(
                "tx: Alice -> Bob, 1.5 ETH",
                "tx: Bob -> Carol, 0.8 ETH",
                "tx: Carol -> Dave, 2.0 ETH",
                "tx: Dave -> Eve, 0.3 ETH"
        );

        EthereumMerkleTree tree1 = new EthereumMerkleTree();
        tree1.build(original);
        String root1 = tree1.getRootHex();

        List<String> tampered = Arrays.asList(
                "tx: Alice -> Bob, 1.5 ETH",
                "tx: Bob -> Carol, 0.8 ETH",
                "tx: Carol -> Dave, 99.9 ETH",  // ← tampered!
                "tx: Dave -> Eve, 0.3 ETH"
        );

        EthereumMerkleTree tree2 = new EthereumMerkleTree();
        tree2.build(tampered);
        String root2 = tree2.getRootHex();

        System.out.println("\nOriginal Root : " + root1);
        System.out.println("Tampered Root : " + root2);
        System.out.println(root1.equals(root2)
                ? "\n✅ Roots match — no tampering"
                : "\n⚠️  TAMPER DETECTED — Keccak-256 roots do NOT match!");
    }

    // =========================================================================
    // Demo 3 — Inclusion proof (OpenZeppelin-compatible)
    // =========================================================================
    private static void demo3_InclusionProof() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 3 — Inclusion Proof (OpenZeppelin MerkleProof compatible)");
        System.out.println("=".repeat(60));

        List<String> allowlist = Arrays.asList(
                "0xAb8483F64d9C6d1EcF9b849Ae677dD3315835cb2",
                "0x5B38Da6a701c568545dCfcB03FcB875f56beddC4",
                "0x4B20993Bc481177ec7E8f571ceCaE8A9e22C02db",
                "0x78731D3Ca6b7E34aC0F824c42a7cC18A495cabaB",
                "0x617F2E2fD72FD9D5503197092AC168c91465E7f2",
                "0x17F6AD8Ef982297579C203069C1DbfFE4348c208"
        );

        EthereumMerkleTree tree = new EthereumMerkleTree();
        tree.build(allowlist);
        String root = tree.getRootHex();

        int targetIdx = 2;
        String targetAddress = allowlist.get(targetIdx);
        List<String> proof = tree.getProof(targetIdx);

        System.out.println("\nProving membership of address (index " + targetIdx + "):");
        System.out.println("  " + targetAddress);
        System.out.println("\nProof (" + proof.size() + " sibling hashes):");
        proof.forEach(h -> System.out.println("  " + h));

        boolean valid = EthereumMerkleTree.verify(targetAddress, proof, root);
        System.out.println("\nVerification: " + (valid ? "✅ VALID — address is in the allowlist" : "❌ INVALID"));

        // Attempt with a non-allowlisted address
        String stranger = "0xdD870fA1b7C4700F2BD7f44238821C26f7392148";
        boolean strangerResult = EthereumMerkleTree.verify(stranger, proof, root);
        System.out.println("Stranger address: " + (strangerResult ? "✅ VALID (false positive!)" : "❌ REJECTED — not in allowlist"));

        System.out.println("\n--- Web3j Key APIs Used ---");
        System.out.println("  Hash.sha3(byte[])         — Keccak-256 (Ethereum's hash function)");
        System.out.println("  Numeric.toHexString(byte[])  — bytes → 0x-prefixed hex");
        System.out.println("  Numeric.hexStringToByteArray — 0x hex → bytes");
        System.out.println("  Sorted-pair hashing          — OpenZeppelin MerkleProof.sol compatible");
    }
}
