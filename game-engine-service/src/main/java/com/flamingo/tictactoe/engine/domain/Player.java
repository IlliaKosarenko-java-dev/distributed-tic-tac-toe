package com.flamingo.tictactoe.engine.domain;

public enum Player {

    X,
    O;

    public Player opponent() {
        return this == X ? O : X;
    }
}
