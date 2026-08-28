package com.flamingo.tictactoe.session.service.exception;

import java.util.UUID;
public class SessionNotFoundException extends RuntimeException {

    private final UUID sessionId;

    public SessionNotFoundException(UUID sessionId) {
        super("No session with id %s".formatted(sessionId));
        this.sessionId = sessionId;
    }

    public UUID sessionId() {
        return sessionId;
    }
}
