package org.example.app.core.ledger;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Small helpers for the canonical binary encodings used in hashing and signing. */
final class Bytes {
    private Bytes() {
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] p : parts) {
            length += p.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] p : parts) {
            buffer.put(p);
        }
        return buffer.array();
    }

    static byte[] u64(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    static byte[] u32(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /** Unsigned big-endian, left padded to exactly {@code length} bytes. */
    static byte[] unsigned(java.math.BigInteger value, int length) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Negative value");
        }
        byte[] raw = value.toByteArray();
        int offset = raw.length > 1 && raw[0] == 0 ? 1 : 0;
        int significant = raw.length - offset;
        if (significant > length) {
            throw new IllegalArgumentException("Value does not fit in " + length + " bytes");
        }
        byte[] out = new byte[length];
        System.arraycopy(raw, offset, out, length - significant, significant);
        return out;
    }

    static boolean equal(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }
}
