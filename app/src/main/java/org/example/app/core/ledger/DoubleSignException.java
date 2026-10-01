package org.example.app.core.ledger;

/** Thrown by a signer that is asked to vote for two different blocks at the same height. */
public class DoubleSignException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DoubleSignException(String message) {
        super(message);
    }
}
