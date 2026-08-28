package com.flamingo.tictactoe.engine.repository.mongo;

/**
 * A stored document that cannot be turned back into a game.
 *
 * <p>This is a server-side data fault, not a bad request, so it stays a 500 — but a nameless
 * 500 is nearly impossible to chase. Carrying the game id and the offending field means the
 * log line and the response both point at the document to look at.
 */
public class CorruptGameDocumentException extends RuntimeException {

    private final String gameId;

    public CorruptGameDocumentException(String gameId, String problem) {
        super("Game document %s cannot be read: %s".formatted(gameId, problem));
        this.gameId = gameId;
    }

    public String gameId() {
        return gameId;
    }
}
