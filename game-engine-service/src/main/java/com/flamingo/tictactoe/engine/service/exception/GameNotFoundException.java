package com.flamingo.tictactoe.engine.service.exception;

import java.util.UUID;
public class GameNotFoundException extends RuntimeException {

    private final UUID gameId;

    public GameNotFoundException(UUID gameId) {
        super("No game with id %s".formatted(gameId));
        this.gameId = gameId;
    }

    public UUID gameId() {
        return gameId;
    }
}
