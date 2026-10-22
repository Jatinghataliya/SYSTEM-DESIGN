package com.example.rsyncguava;

/**
 * Adler-32 Rolling Checksum implementation (used in Rsync protocol v29 and zlib).
 * <p>
 * Adler-32 uses modulo 65521 (the largest prime &lt; 2^16).
 * <ul>
 *   <li>A = (1 + sum(c_i)) mod 65521</li>
 *   <li>B = (n + sum((n - i) * c_i)) mod 65521</li>
 * </ul>
 * Combined 32-bit checksum is (B &lt;&lt; 16) | A.
 */
public final class Adler32RollingChecksum {

    public static final int MOD_ADLER = 65521;

    private int a;
    private int b;
    private int windowSize;

    public Adler32RollingChecksum() {
        reset();
    }

    public static long compute(byte[] data, int offset, int length) {
        int s1 = 1;
        int s2 = 0;
        for (int i = 0; i < length; i++) {
            int val = data[offset + i] & 0xFF;
            s1 = (s1 + val) % MOD_ADLER;
            s2 = (s2 + s1) % MOD_ADLER;
        }
        return ((long) s2 << 16) | (long) s1;
    }

    public void init(byte[] data, int offset, int length) {
        this.windowSize = length;
        int s1 = 1;
        int s2 = 0;
        for (int i = 0; i < length; i++) {
            int val = data[offset + i] & 0xFF;
            s1 = (s1 + val) % MOD_ADLER;
            s2 = (s2 + s1) % MOD_ADLER;
        }
        this.a = s1;
        this.b = s2;
    }

    public long roll(byte outByte, byte inByte) {
        int x = outByte & 0xFF;
        int y = inByte & 0xFF;

        int newA = (this.a - x + y) % MOD_ADLER;
        if (newA < 0) newA += MOD_ADLER;

        // B_new = (B_old - n * x + (A_new - 1)) mod 65521
        long newB = ((long) this.b - (long) this.windowSize * x + (newA - 1)) % MOD_ADLER;
        while (newB < 0) newB += MOD_ADLER;

        this.a = newA;
        this.b = (int) newB;

        return getValue();
    }

    public long getValue() {
        return (((long) this.b << 16) | (long) this.a) & 0xFFFFFFFFL;
    }

    public void reset() {
        this.a = 1;
        this.b = 0;
        this.windowSize = 0;
    }

    public int getWindowSize() {
        return windowSize;
    }
}
