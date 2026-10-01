package org.example.app.core.ledger;

import org.example.app.core.crypto.Secp256k1;

import java.util.Locale;

/** Strict handling of account addresses: {@code 0x} followed by exactly 40 hex digits. */
public final class Addresses {
    public static final int LENGTH = 20;

    private Addresses() {
    }

    /** Returns the canonical form (0x + 40 lowercase hex) or throws if {@code address} is not a valid address. */
    public static String canonical(String address) {
        return "0x" + Secp256k1.toHex(bytes(address));
    }

    public static byte[] bytes(String address) {
        if (address == null) {
            throw new IllegalArgumentException("Address is required");
        }
        String trimmed = address.trim().toLowerCase(Locale.ROOT);
        byte[] raw = Secp256k1.fromHex(trimmed.startsWith("0x") ? trimmed : "0x" + trimmed);
        if (raw.length != LENGTH) {
            throw new IllegalArgumentException("Address must be 20 bytes: " + address);
        }
        return raw;
    }

    public static boolean isValid(String address) {
        try {
            bytes(address);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
