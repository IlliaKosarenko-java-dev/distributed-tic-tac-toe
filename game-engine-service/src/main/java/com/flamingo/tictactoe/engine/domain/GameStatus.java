package com.flamingo.tictactoe.engine.domain;

public enum GameStatus {

    IN_PROGRESS,
    X_WON,
    O_WON,
    DRAW;

    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }

    public static GameStatus wonBy(Player player) {
        return player == Player.X ? X_WON : O_WON;
    }
}
