package com.flamingo.tictactoe.session.service.exception;

import java.util.UUID;
public class ConcurrentSessionUpdateException extends RuntimeException {

    private final UUID sessionId;
    private final long expectedVersion;

    public ConcurrentSessionUpdateException(UUID sessionId, long expectedVersion) {
        super("Session %s was modified concurrently; expected version %d"
                .formatted(sessionId, expectedVersion));
        this.sessionId = sessionId;
        this.expectedVersion = expectedVersion;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
