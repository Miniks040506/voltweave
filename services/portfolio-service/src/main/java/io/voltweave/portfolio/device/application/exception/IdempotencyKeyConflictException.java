package io.voltweave.portfolio.device.application.exception;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException() {
        super("Idempotency key was already used for another request");
    }
}
