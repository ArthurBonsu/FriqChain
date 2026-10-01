package org.example.app.core.ledger;

/** Thrown when a block fails validation; the message names the first rule that was broken. */
public class InvalidBlockException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidBlockException(String message) {
        super(message);
    }
}
