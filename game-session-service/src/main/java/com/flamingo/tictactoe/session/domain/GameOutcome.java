package com.flamingo.tictactoe.session.domain;

/** Mirrors the status the engine reports back for a game. */
public enum GameOutcome {

    IN_PROGRESS,
    X_WON,
    O_WON,
    DRAW;

    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }
}
