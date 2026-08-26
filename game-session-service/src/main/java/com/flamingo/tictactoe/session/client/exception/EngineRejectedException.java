package com.flamingo.tictactoe.session.client.exception;

/**
 * Permanent: the engine understood the request and refused it — an occupied cell, the wrong
 * player's turn, a finished game, an unknown id.
 *
 * <p>Never retried. Replaying a rejected move only produces the same refusal, and treating a
 * verdict as a glitch would turn one clear error into three and a misleading log.
 */
public class EngineRejectedException extends EngineException {

    private final int status;
    private final String code;

    public EngineRejectedException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    /** The engine's machine-readable code, e.g. CELL_OCCUPIED or GAME_NOT_FOUND. */
    public String code() {
        return code;
    }
}
