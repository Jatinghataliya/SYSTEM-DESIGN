package com.example.rsync;

import java.util.List;

/**
 * Rsync coordinator facade providing end-to-end sync capabilities.
 */
public final class Rsync {

    private final int blockSize;
    private final RsyncSignatureGenerator signatureGenerator;
    private final RsyncDeltaGenerator deltaGenerator;
    private final RsyncReconstructor reconstructor;

    public Rsync() {
        this(RsyncSignatureGenerator.DEFAULT_BLOCK_SIZE);
    }

    public Rsync(int blockSize) {
        this.blockSize = blockSize;
        this.signatureGenerator = new RsyncSignatureGenerator(blockSize);
        this.deltaGenerator = new RsyncDeltaGenerator();
        this.reconstructor = new RsyncReconstructor();
    }

    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Receiver side: Generate signature table for local basis file.
     */
    public SignatureTable generateSignatures(byte[] basis) {
        return signatureGenerator.generate(basis);
    }

    /**
     * Sender side: Compute deltas between source data and basis signature table.
     */
    public List<DeltaOp> computeDelta(byte[] source, SignatureTable basisSignatures) {
        return deltaGenerator.generateDelta(source, basisSignatures);
    }

    /**
     * Receiver side: Reconstruct source file from basis data and delta ops.
     */
    public byte[] reconstruct(byte[] basis, List<DeltaOp> deltas, SignatureTable table) {
        return reconstructor.reconstruct(basis, deltas, table);
    }

    /**
     * End-to-end simulation of Rsync sync between basis and source.
     */
    public RsyncSyncResult sync(byte[] basis, byte[] source) {
        SignatureTable sigTable = generateSignatures(basis);
        List<DeltaOp> deltas = computeDelta(source, sigTable);
        byte[] reconstructed = reconstruct(basis, deltas, sigTable);
        RsyncStats stats = RsyncStats.compute(basis, source, sigTable, deltas);

        return new RsyncSyncResult(reconstructed, deltas, sigTable, stats);
    }
}
