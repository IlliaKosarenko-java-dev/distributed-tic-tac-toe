package com.flamingo.tictactoe.session.service.exception;

public class ConcurrentSessionUpdateException extends RuntimeException {

    private final String sessionId;
    private final long expectedVersion;

    public ConcurrentSessionUpdateException(String sessionId, long expectedVersion) {
        super("Session %s was modified concurrently; expected version %d"
                .formatted(sessionId, expectedVersion));
        this.sessionId = sessionId;
        this.expectedVersion = expectedVersion;
    }

    public String sessionId() {
        return sessionId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
