package com.flamingo.tictactoe.engine.domain;

public enum MoveRejectionReason {

    CELL_OCCUPIED,
    NOT_PLAYERS_TURN,
    GAME_ALREADY_FINISHED
}
