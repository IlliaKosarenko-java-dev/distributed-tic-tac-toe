package com.flamingo.tictactoe.session.service.exception;

public class SessionNotFoundException extends RuntimeException {

    private final String sessionId;

    public SessionNotFoundException(String sessionId) {
        super("No session with id %s".formatted(sessionId));
        this.sessionId = sessionId;
    }

    public String sessionId() {
        return sessionId;
    }
}
