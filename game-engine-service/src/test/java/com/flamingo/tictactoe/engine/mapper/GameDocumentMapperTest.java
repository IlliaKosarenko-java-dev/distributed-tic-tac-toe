package com.flamingo.tictactoe.engine.mapper;

import java.util.UUID;
import com.flamingo.tictactoe.engine.repository.StoredGame;
import com.flamingo.tictactoe.engine.repository.mongo.GameDocument;
import com.flamingo.tictactoe.engine.domain.Game;
import com.flamingo.tictactoe.engine.domain.GameStatus;
import com.flamingo.tictactoe.engine.domain.Move;
import com.flamingo.tictactoe.engine.domain.Player;
import com.flamingo.tictactoe.engine.domain.Position;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;


class GameDocumentMapperTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-01-01T10:05:00Z");

    private static final UUID GAME_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final GameDocumentMapper mapper = new GameDocumentMapper();

    private static Game gameAfter(int... cells) {
        Game game = Game.newGame(GAME_ID, Player.X);
        for (int cell : cells) {
            game = game.applyMove(Move.of(game.nextPlayer(), cell));
        }
        return game;
    }

    @Test
    void carriesAnUnplayedGameOntoTheDocument() {
        GameDocument document = mapper.toDocument(gameAfter(), null, CREATED, CREATED);

        assertThat(document.id()).isEqualTo(GAME_ID.toString());
        assertThat(document.board()).hasSize(9).containsOnlyNulls();
        assertThat(document.nextPlayer()).isEqualTo(Player.X);
        assertThat(document.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(document.moveCount()).isZero();
        assertThat(document.winningLine()).isNull();
        assertThat(document.winner()).isNull();
        assertThat(document.version()).isNull();
        assertThat(document.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void splitsAWinningLineIntoPositionsAndWinner() {
        GameDocument document = mapper.toDocument(gameAfter(0, 3, 1, 4, 2), 5L, CREATED, UPDATED);

        assertThat(document.status()).isEqualTo(GameStatus.X_WON);
        assertThat(document.winningLine()).containsExactly(0, 1, 2);
        assertThat(document.winner()).isEqualTo(Player.X);
        assertThat(document.version()).isEqualTo(5L);
        assertThat(document.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void recombinesTheWinningLineOnTheWayBack() {
        GameDocument document = mapper.toDocument(gameAfter(0, 3, 1, 4, 2), 5L, CREATED, UPDATED);

        Game restored = mapper.toDomain(document);

        assertThat(restored.winningLine()).hasValueSatisfying(line -> {
            assertThat(line.player()).isEqualTo(Player.X);
            assertThat(line.positions()).containsExactly(0, 1, 2);
        });
    }

    @Test
    void roundTripsAGameInProgressWithoutLosingFreeCells() {
        Game original = gameAfter(4, 0, 8);

        Game restored = mapper.toDomain(mapper.toDocument(original, 3L, CREATED, UPDATED));

        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.board()).isEqualTo(original.board());
        assertThat(restored.nextPlayer()).isEqualTo(original.nextPlayer());
        assertThat(restored.status()).isEqualTo(original.status());
        assertThat(restored.moveCount()).isEqualTo(original.moveCount());
        assertThat(restored.board().at(Position.of(1)))
                .as("an empty cell must come back empty, not as a stray value")
                .isEmpty();
        assertThat(restored.winningLine()).isEmpty();
    }

    @Test
    void roundTripsADrawnGame() {
        Game drawn = gameAfter(0, 1, 2, 4, 3, 5, 7, 6, 8);
        assertThat(drawn.status()).isEqualTo(GameStatus.DRAW);

        Game restored = mapper.toDomain(mapper.toDocument(drawn, 9L, CREATED, UPDATED));

        assertThat(restored.status()).isEqualTo(GameStatus.DRAW);
        assertThat(restored.board().isFull()).isTrue();
        assertThat(restored.winningLine()).isEmpty();
    }

    @Test
    void treatsAMissingVersionAsZeroRatherThanFailing() {
        StoredGame stored = mapper.toStoredGame(mapper.toDocument(gameAfter(), null, CREATED, CREATED));

        assertThat(stored.version()).isZero();
    }

    @Test
    void carriesTheStoredVersionOntoTheStoredGame() {
        StoredGame stored = mapper.toStoredGame(mapper.toDocument(gameAfter(4), 7L, CREATED, UPDATED));

        assertThat(stored.version()).isEqualTo(7L);
        assertThat(stored.game().moveCount()).isEqualTo(1);
    }
}
