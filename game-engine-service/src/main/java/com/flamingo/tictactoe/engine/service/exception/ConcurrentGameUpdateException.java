package com.flamingo.tictactoe.engine.service.exception;

/**
 * Another writer changed the game between our read and our write. Unlike a rejected move
 * this is not a verdict on the move itself — the same move may well succeed on a retry —
 * so it is reported separately and the caller decides whether to re-read and try again.
 */
public class ConcurrentGameUpdateException extends RuntimeException {

    private final String gameId;
    private final long expectedVersion;

    public ConcurrentGameUpdateException(String gameId, long expectedVersion) {
        super("Game %s was modified concurrently; expected version %d".formatted(gameId, expectedVersion));
        this.gameId = gameId;
        this.expectedVersion = expectedVersion;
    }

    public String gameId() {
        return gameId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }
}
