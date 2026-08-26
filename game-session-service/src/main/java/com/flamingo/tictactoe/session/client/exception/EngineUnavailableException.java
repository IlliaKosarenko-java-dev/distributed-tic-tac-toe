package com.flamingo.tictactoe.session.client.exception;

/**
 * Transient: a timeout, a refused connection, or a 5xx. Retrying is reasonable, and the
 * circuit breaker counts these.
 */
public class EngineUnavailableException extends EngineException {

    public EngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public EngineUnavailableException(String message) {
        super(message);
    }
}
