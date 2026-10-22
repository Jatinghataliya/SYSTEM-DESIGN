package com.example.rsynccommons;

/**
 * Fast 32-bit Fletcher/Tridgell rolling checksum.
 */
public final class Fletcher32RollingChecksum {

    private int s1;
    private int s2;
    private int windowSize;

    public Fletcher32RollingChecksum() {
        reset();
    }

    public static long compute(byte[] data, int offset, int length) {
        int a = 0;
        int b = 0;
        for (int i = 0; i < length; i++) {
            int val = data[offset + i] & 0xFF;
            a = (a + val) & 0xFFFF;
            b = (b + (length - i) * val) & 0xFFFF;
        }
        return (((long) b << 16) | (long) a) & 0xFFFFFFFFL;
    }

    public void init(byte[] data, int offset, int length) {
        this.windowSize = length;
        this.s1 = 0;
        this.s2 = 0;
        for (int i = 0; i < length; i++) {
            int val = data[offset + i] & 0xFF;
            this.s1 = (this.s1 + val) & 0xFFFF;
            this.s2 = (this.s2 + (length - i) * val) & 0xFFFF;
        }
    }

    public long roll(byte outByte, byte inByte) {
        int x = outByte & 0xFF;
        int y = inByte & 0xFF;

        this.s1 = (this.s1 - x + y) & 0xFFFF;
        this.s2 = (this.s2 - this.windowSize * x + this.s1) & 0xFFFF;

        return getValue();
    }

    public long getValue() {
        return (((long) this.s2 << 16) | (long) this.s1) & 0xFFFFFFFFL;
    }

    public void reset() {
        this.s1 = 0;
        this.s2 = 0;
        this.windowSize = 0;
    }
}
