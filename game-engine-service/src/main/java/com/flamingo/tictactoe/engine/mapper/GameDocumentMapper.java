package com.flamingo.tictactoe.engine.mapper;

import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.repository.mongo.GameDocument;
import com.flamingo.tictactoe.engine.domain.Board;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.domain.WinningLine;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Translates between the {@link Game} aggregate and its stored form.
 */
@Component
public class GameDocumentMapper {

    /**
     * @param version   the version the game was read at, or null for a document being inserted
     * @param createdAt preserved from the existing document so a move does not rewrite it
     */
    public GameDocument toDocument(Game game, Long version, Instant createdAt, Instant updatedAt) {
        WinningLine line = game.winningLine().orElse(null);
        return new GameDocument(
                game.id(),
                game.board().cells(),
                game.nextPlayer(),
                game.status(),
                game.moveCount(),
                line == null ? null : line.positions(),
                line == null ? null : line.player(),
                version,
                createdAt,
                updatedAt);
    }

    public Game toDomain(GameDocument document) {
        return Game.restore(
                document.id(),
                Board.of(document.board().toArray(new Player[0])),
                document.nextPlayer(),
                document.status(),
                document.moveCount(),
                document.winningLine() == null
                        ? null
                        : new WinningLine(document.winner(), document.winningLine()));
    }

    /** A version of null means the document has not been through an insert yet. */
    public StoredGame toStoredGame(GameDocument document) {
        return new StoredGame(toDomain(document), document.version() == null ? 0L : document.version());
    }
}
