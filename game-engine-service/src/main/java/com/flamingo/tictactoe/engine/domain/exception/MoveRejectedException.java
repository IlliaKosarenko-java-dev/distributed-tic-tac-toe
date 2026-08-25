package com.flamingo.tictactoe.engine.domain.exception;


import com.flamingo.tictactoe.engine.domain.MoveRejectionReason;

/**
 * The move was well formed but illegal in the current game state. These are deterministic
 * answers, not transient failures — retrying one will always produce the same result.
 */
public class MoveRejectedException extends RuntimeException {

    private final MoveRejectionReason reason;

    public MoveRejectedException(MoveRejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public MoveRejectionReason reason() {
        return reason;
    }
}
