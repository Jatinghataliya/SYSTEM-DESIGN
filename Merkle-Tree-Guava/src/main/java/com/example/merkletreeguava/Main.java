package com.example.merkletreeguava;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.util.Arrays;
import java.util.List;

/**
 * Three live demos of the Guava-based Merkle Tree:
 *
 * 1. File integrity system — SHA-256 Merkle root over a software release bundle.
 * 2. Hash function comparison — same data, SHA-256 vs SHA-512 vs Murmur3.
 * 3. Inclusion proof — verify one file without revealing the others.
 *
 * Run: mvn compile exec:java
 */
@SuppressWarnings("deprecation")  // Hashing.murmur3_128 stable but @Beta in older versions
public class Main {

    public static void main(String[] args) {
        demo1_FileIntegrity();
        demo2_HashFunctionComparison();
        demo3_InclusionProof();
    }

    // =========================================================================
    // Demo 1 — Software release file integrity
    // =========================================================================
    private static void demo1_FileIntegrity() {
        System.out.println("=".repeat(60));
        System.out.println("DEMO 1 — Software Release File Integrity (Guava SHA-256)");
        System.out.println("=".repeat(60));

        List<String> releaseFiles = Arrays.asList(
                "app-core-1.0.0.jar:checksum=a1b2c3",
                "app-ui-1.0.0.jar:checksum=d4e5f6",
                "app-api-1.0.0.jar:checksum=g7h8i9",
                "app-db-1.0.0.jar:checksum=j1k2l3",
                "config-prod.yaml:checksum=m4n5o6",
                "startup.sh:checksum=p7q8r9"
        );

        GuavaMerkleTree tree = new GuavaMerkleTree(Hashing.sha256());
        tree.build(releaseFiles);

        System.out.println("\nRelease bundle files:");
        List<HashCode> leaves = tree.getLeaves();
        for (int i = 0; i < releaseFiles.size(); i++) {
            System.out.printf("  [%d] %-40s  →  %s...%n",
                    i, releaseFiles.get(i), leaves.get(i).toString().substring(0, 16));
        }
        System.out.println("\nHash Function : " + tree.getAlgorithmName());
        System.out.println("Merkle Root   : " + tree.getRootHex());
        System.out.println("\n(Publish this root hash alongside the release. Any altered file");
        System.out.println(" will produce a different root, immediately detecting tampering.)");
    }

    // =========================================================================
    // Demo 2 — Hash function comparison (Guava's pluggable HashFunction)
    // =========================================================================
    @SuppressWarnings("UnstableApiUsage")
    private static void demo2_HashFunctionComparison() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 2 — Pluggable Hash Functions (Guava's Unique Advantage)");
        System.out.println("=".repeat(60));

        List<String> data = Arrays.asList(
                "block-0:genesis",
                "block-1:tx-batch-A",
                "block-2:tx-batch-B",
                "block-3:tx-batch-C"
        );

        HashFunction[] functions = {
                Hashing.sha256(),
                Hashing.sha512(),
                Hashing.murmur3_128()
        };

        System.out.println("\nSame data — different hash functions:");
        System.out.printf("  %-20s  %s%n", "HashFunction", "Merkle Root");
        System.out.println("  " + "-".repeat(80));
        for (HashFunction fn : functions) {
            GuavaMerkleTree tree = new GuavaMerkleTree(fn);
            tree.build(data);
            System.out.printf("  %-20s  %s%n", fn, tree.getRootHex());
        }

        System.out.println("\nNote: Murmur3 is NOT cryptographically secure — use only for");
        System.out.println("  non-security applications (e.g. distributed cache sharding).");
    }

    // =========================================================================
    // Demo 3 — Inclusion proof
    // =========================================================================
    private static void demo3_InclusionProof() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("DEMO 3 — Inclusion Proof with Guava SHA-256");
        System.out.println("=".repeat(60));

        List<String> files = Arrays.asList(
                "README.md",
                "LICENSE",
                "pom.xml",
                "src/Main.java",
                "src/MerkleTree.java",
                "src/HashUtils.java",
                "docs/design.md",
                "docs/api.md"
        );

        HashFunction sha256 = Hashing.sha256();
        GuavaMerkleTree tree = new GuavaMerkleTree(sha256);
        tree.build(files);
        String root = tree.getRootHex();

        int targetIndex = 4;
        String targetFile = files.get(targetIndex);
        List<HashCode> proof = tree.getProof(targetIndex);

        System.out.println("\nProving inclusion of: \"" + targetFile + "\" (index " + targetIndex + ")");
        System.out.println("Merkle Root: " + root);
        System.out.println("Proof (" + proof.size() + " sibling hashes):");
        proof.forEach(h -> System.out.println("  " + h));

        boolean valid = GuavaMerkleTree.verify(sha256, targetFile, targetIndex, proof, root);
        System.out.println("\nVerification: " + (valid ? "✅ VALID — file is in the release" : "❌ INVALID"));

        // Attempt with a modified file name
        String modified = "src/MerkleTree_HACKED.java";
        boolean modified_result = GuavaMerkleTree.verify(sha256, modified, targetIndex, proof, root);
        System.out.println("Modified file: " + (modified_result ? "✅ VALID (false positive!)" : "❌ REJECTED — tampered data detected"));

        System.out.println("\n--- Guava Key APIs Used ---");
        System.out.println("  Hashing.sha256()              — SHA-256 HashFunction factory");
        System.out.println("  Hashing.sha512()              — SHA-512 HashFunction factory");
        System.out.println("  Hashing.murmur3_128()         — Murmur3 (non-crypto, fast)");
        System.out.println("  hashFn.hashString(s, charset) — hash a string → HashCode");
        System.out.println("  HashCode.toString()           — hex representation");
        System.out.println("  (swap HashFunction = swap the entire algorithm with one line)");
    }
}
