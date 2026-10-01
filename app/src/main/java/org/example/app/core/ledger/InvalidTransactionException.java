package org.example.app.core.ledger;

/** Thrown by an executor when a transaction cannot be applied. */
public class InvalidTransactionException extends Exception {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
