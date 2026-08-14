package io.voltweave.settlement.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key was reused with another request");
    }
}
