package net.zedaris.fabrarium.persistence;

public final class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
