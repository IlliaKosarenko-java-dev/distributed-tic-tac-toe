package com.flamingo.tictactoe.engine.service.exception;

public class GameNotFoundException extends RuntimeException {

    private final String gameId;

    public GameNotFoundException(String gameId) {
        super("No game with id %s".formatted(gameId));
        this.gameId = gameId;
    }

    public String gameId() {
        return gameId;
    }
}
