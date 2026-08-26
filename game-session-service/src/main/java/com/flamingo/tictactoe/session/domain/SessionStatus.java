package com.flamingo.tictactoe.session.domain;

/**
 * Lifecycle of a session.
 *
 * <pre>
 *   CREATED ──claim──► RUNNING ──┬──► FINISHED   (the game reached a result)
 *                                └──► FAILED     (the engine could not be reached)
 * </pre>
 *
 * Only CREATED may be claimed, which is what stops two runners driving one game.
 */
public enum SessionStatus {

    CREATED,
    RUNNING,
    FINISHED,
    FAILED;

    public boolean isTerminal() {
        return this == FINISHED || this == FAILED;
    }
}
