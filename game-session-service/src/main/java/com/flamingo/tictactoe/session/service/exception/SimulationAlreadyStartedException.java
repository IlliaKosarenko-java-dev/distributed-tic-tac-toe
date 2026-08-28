package com.flamingo.tictactoe.session.service.exception;

import java.util.UUID;
import com.flamingo.tictactoe.session.domain.SessionStatus;

/**
 * Someone already claimed this session. Not a failure of the request so much as a race the
 * caller lost — reported so a retry cannot quietly start a second runner on one game.
 */
public class SimulationAlreadyStartedException extends RuntimeException {

    private final UUID sessionId;
    private final SessionStatus currentStatus;

    public SimulationAlreadyStartedException(UUID sessionId, SessionStatus currentStatus) {
        super("Simulation for session %s cannot start: it is already %s"
                .formatted(sessionId, currentStatus));
        this.sessionId = sessionId;
        this.currentStatus = currentStatus;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public SessionStatus currentStatus() {
        return currentStatus;
    }
}
