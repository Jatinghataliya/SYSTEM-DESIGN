package com.example.rsync;

/**
 * Implementation of Andrew Tridgell's rolling checksum (32-bit) used in Rsync.
 * <p>
 * It computes two 16-bit sums:
 * <ul>
 *   <li>s1 = sum(c_i) mod 65536</li>
 *   <li>s2 = sum((windowSize - i) * c_i) mod 65536</li>
 * </ul>
 * The combined 32-bit checksum is (s1 &amp; 0xFFFF) | ((s2 &amp; 0xFFFF) &lt;&lt; 16).
 * <p>
 * Moving the sliding window by 1 byte (rolling out old byte 'x', rolling in new byte 'y')
 * is done in O(1) time without re-scanning the window.
 */
public final class RollingChecksum {

    private static final int M = 65536; // 2^16

    private int s1;
    private int s2;
    private int windowSize;

    public RollingChecksum() {
        this.s1 = 0;
        this.s2 = 0;
        this.windowSize = 0;
    }

    /**
     * Compute rolling checksum for a byte array slice from scratch.
     *
     * @param buffer the data buffer
     * @param offset starting index
     * @param length number of bytes in window
     * @return 32-bit unsigned checksum value (represented as long)
     */
    public static long compute(byte[] buffer, int offset, int length) {
        int a = 0;
        int b = 0;
        for (int i = 0; i < length; i++) {
            int val = buffer[offset + i] & 0xFF;
            a = (a + val) & 0xFFFF;
            b = (b + (length - i) * val) & 0xFFFF;
        }
        return toUint32(a, b);
    }

    /**
     * Initialize the rolling checksum state with an initial window.
     *
     * @param buffer the data buffer
     * @param offset starting index
     * @param length number of bytes in window
     */
    public void init(byte[] buffer, int offset, int length) {
        this.s1 = 0;
        this.s2 = 0;
        this.windowSize = length;
        for (int i = 0; i < length; i++) {
            int val = buffer[offset + i] & 0xFF;
            this.s1 = (this.s1 + val) & 0xFFFF;
            this.s2 = (this.s2 + (length - i) * val) & 0xFFFF;
        }
    }

    /**
     * Roll the window forward by 1 byte in O(1) time.
     *
     * @param outByte the byte sliding out of the window (left edge)
     * @param inByte  the byte sliding into the window (right edge)
     * @return updated 32-bit checksum
     */
    public long roll(byte outByte, byte inByte) {
        int x = outByte & 0xFF;
        int y = inByte & 0xFF;

        this.s1 = (this.s1 - x + y) & 0xFFFF;
        this.s2 = (this.s2 - this.windowSize * x + this.s1) & 0xFFFF;

        return getValue();
    }

    /**
     * Get the current 32-bit rolling checksum as an unsigned long.
     */
    public long getValue() {
        return toUint32(this.s1, this.s2);
    }

    /**
     * Reset checksum state to 0.
     */
    public void reset() {
        this.s1 = 0;
        this.s2 = 0;
        this.windowSize = 0;
    }

    public int getWindowSize() {
        return windowSize;
    }

    private static long toUint32(int a, int b) {
        long high = ((long) (b & 0xFFFF)) << 16;
        long low = (long) (a & 0xFFFF);
        return (high | low) & 0xFFFFFFFFL;
    }
}
